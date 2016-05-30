package se.chalmers.phrasebook.gui.activities;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;

import org.grammaticalframework.ui.android.R;
import se.chalmers.phrasebook.backend.Model;
import se.chalmers.phrasebook.backend.syntax.*;
import se.chalmers.phrasebook.gui.fragments.PhraseListFragment;
import se.chalmers.phrasebook.gui.fragments.TranslatorFragment;

public class NavigationActivity extends FragmentActivity {

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private Fragment mContent;

    private Model mModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        mModel = Model.getInstance();
        mTitle = getTitle();
        
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.container, PhraseListFragment.newInstance("Phrasebook"));
        transaction.commit();
    }

    public void switchContent(Fragment fragment, String message) {
        mContent = fragment;

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(message);
        transaction.commit();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
            getSupportFragmentManager().popBackStack();
            getSupportFragmentManager().beginTransaction().commit();
            //Switches to the previous entry on the stack to ensure
            //that mContent is preserved
            mContent = getSupportFragmentManager().getFragments()
                    .get(getSupportFragmentManager().getFragments().size() - 2);
        } else {
			super.onBackPressed();
		}
    }

    public void setToTranslationFragment(SyntaxTree phrase) {
        switchContent(TranslatorFragment.newInstance(phrase), "");
    }
}
