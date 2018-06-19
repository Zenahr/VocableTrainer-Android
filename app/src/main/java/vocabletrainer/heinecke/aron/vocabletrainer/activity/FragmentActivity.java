package vocabletrainer.heinecke.aron.vocabletrainer.activity;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import vocabletrainer.heinecke.aron.vocabletrainer.R;
import vocabletrainer.heinecke.aron.vocabletrainer.fragment.BaseFragment;

/**
 * Base class for fragment activities<br>
 *     This includes back stack function & callbacks as well as helper functions
 */
public abstract class FragmentActivity extends AppCompatActivity{

    private static final String TAG = "FragmentActivity";
    private Fragment currentFragment;
    private Fragment rootFragment;

    /**
     * Interface to implement by fragments that want to be notifified
     */
    public interface BackButtonListener {
        /**
         * Called when back button is pressed<br>
         *     Used to communicate between activity & fragment
         * @return true indicates that the activity can be closed
         */
        boolean onBackPressed();
    }

    BackButtonListener backButtonListener;

    /**
     * Returns the action bar<br>
     *     used by fragments
     * @return ActionBar or Null if none exists
     */
    public ActionBar getSupportActionBar(){
        return super.getSupportActionBar();
    }

    @Override
    public void onBackPressed() {
        if(backButtonListener != null) {
            if (backButtonListener.onBackPressed()) {
                super.onBackPressed();
            }
        } else if(!handleFragmentBack()){
            super.onBackPressed();
        }
    }

    /**
     * Pops the stack & handles fragment back
     * @return false when it's impossible to go back
     */
    protected boolean handleFragmentBack(){
        Log.d(TAG,"handling fragment back "+ getFragmentManager().getBackStackEntryCount());
        if(getFragmentManager().getBackStackEntryCount() > 0){
            Log.d(TAG,"popping stack");
            currentFragment = getCurrentFragment();
            if(getFragmentManager().popBackStackImmediate()) {
                if (currentFragment instanceof BaseFragment) {
                    currentFragment.onResume();
                }
                return true;
            } else {
                Log.w(TAG,"unable to pop backstack");
                return false;
            }
        }else{
            return false;
        }
    }

    /**
     * Returns the current fragment
     * @return
     */
    private Fragment getCurrentFragment(){
        FragmentManager fragmentManager = getSupportFragmentManager();
        int amount;
        Log.d(TAG,"fragment stack:" + (amount = fragmentManager.getBackStackEntryCount()));
        Fragment fr = fragmentManager.findFragmentById(R.id.frame);
        if(fr == null) {
            fr = rootFragment;
        }
        return fr;
    }

    /**
     * Set fragment to show<br>

     *     Replaces current fragment
     * @param fragment
     */
    public void setFragment(final Fragment fragment){
        Log.w(TAG,(fragment instanceof BaseFragment)+""+fragment);
        checkBackButtonListener(fragment);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, fragment).commit();
        currentFragment = fragment;
        rootFragment = fragment;
    }

    /**
     * Check back button listener
     * @param fragment
     */
    private void checkBackButtonListener(final Fragment fragment){
        if(fragment instanceof BackButtonListener) {
            backButtonListener = (BackButtonListener) fragment;
        }else{
            backButtonListener = null;
        }
    }

    /**
     * Adds a new fragment as top element
     * @param fragment
     */
    public void addFragment(final Fragment caller, final Fragment fragment){
        checkBackButtonListener(fragment);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.frame,fragment).addToBackStack(null).remove(caller).commit();
        currentFragment = fragment;
    }
}
