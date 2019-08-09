#[macro_use]
extern crate serde_derive;
#[macro_use]
extern crate failure;
#[macro_use]
extern crate log;

use actix::prelude::*;
use actix_web::{fs, http, middleware::Logger, server, server::KeepAlive, App};
use env_logger;
use mysql::{Opts, OptsBuilder, Pool};
use num_cpus;

mod db;
mod errors;
mod get;
mod post;
mod settings;

use std::thread::sleep;
use std::time::{Duration, Instant};

use db::DbExecutor;
use get::*;
use post::*;
use settings::Settings;

pub struct AppState {
    pub db: Addr<DbExecutor>,
}

#[derive(Default)]
struct SFC;

impl fs::StaticFileConfig for SFC {
    fn is_use_etag() -> bool {
        true
    }

    fn is_use_last_modifier() -> bool {
        true
    }
}

fn main() {
    std::env::set_var("RUST_LOG", "actix_web=warn,survey_backend=debug");
    env_logger::init();

    let settings = match settings::Settings::new() {
        Ok(v) => v,
        Err(e) => {
            error!("{}", e);
            panic!("{}", e);
        }
    };

    let cpus = num_cpus::get();
    let pool = init_pool(&settings, Duration::from_secs(60 * 10));
    info!("Initialized DB");
    let sys = actix::System::new("survey-backend");

    let addr = SyncArbiter::start(cpus, move || DbExecutor(pool.clone()));

    server::new(move || {
        App::with_state(AppState { db: addr.clone() })
            .middleware(Logger::default())
            .resource("/data/add/api", move |r| {
                r.method(http::Method::POST).with(api_post)
            })
            .resource("/data/get/api", move |r| {
                r.method(http::Method::GET).with(api_get)
            })
            .handler(
                "/",
                fs::StaticFiles::with_config("./fs", SFC)
                    .unwrap()
                    .index_file("index.html"),
            )
            .boxed()
    })
    .backlog(8192)
    .keep_alive(KeepAlive::Timeout(1))
    .bind(format!("{}:{}", settings.bind.host, settings.bind.port))
    .expect("Can't bind server!")
    .start();

    sys.run();
}

/// Initialize DB pool, retries otherwise panics after retry_times
fn init_pool(settings: &Settings, retry_duration: Duration) -> Pool {
    trace!("Initializing DB");
    let sleep_time = match retry_duration.as_secs() / 10 {
        x if x < 10 => 5,
        x if x > 30 => 60,
        x => x,
    };
    let start = Instant::now();
    while start.elapsed() < retry_duration {
        let mut builder = OptsBuilder::new();
        builder
            .ip_or_hostname(Some(settings.database.host.clone()))
            .db_name(Some(settings.database.database.clone()))
            .user(Some(settings.database.user.clone()))
            .pass(Some(settings.database.password.clone()))
            .tcp_keepalive_time_ms(Some(60_000 * 5))
            .tcp_port(settings.database.port.clone());
        let opts: Opts = builder.into();
        match Pool::new(opts) {
            Ok(v) => return v,
            Err(e) => {
                error!("Can't connect to DB: {:?}", e);
            }
        }
        sleep(Duration::from_secs(sleep_time));
    }
    panic!(
        "Couldn't connect to DB after {} seconds! Aborting.",
        retry_duration.as_secs()
    );
}
