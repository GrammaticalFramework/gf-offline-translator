package org.grammaticalframework.ui.android;

import java.io.Serializable;
import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ConversationView extends ScrollView {

    private LayoutInflater mInflater;

    private ViewGroup mContent;
    
    private OnClickListener mAlternativesListener;
    private ASR.Listener mSpeechListener;

    public ConversationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);        
    }

    public ConversationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConversationView(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = (ViewGroup) findViewById(R.id.conversation_content);
        mInflater = LayoutInflater.from(getContext());
    }
    		
    private class EditorListener implements OnEditorActionListener, OnClickListener {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if ((actionId & EditorInfo.IME_MASK_ACTION) != 0) {
            	CharSequence text = v.getText();
            	InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                }
                v.setFocusable(false);
                mLastUtterance = v;
                if (mSpeechListener != null)
                	mSpeechListener.onSpeechInput(text.toString().trim());
                return true;
            }
            return false;
        }

		@Override
		public void onClick(View v) {
			v.setFocusableInTouchMode(true);
			v.requestFocus();
	        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
		}
	};

    private EditorListener mEditorListener = new EditorListener();
    private TextView mLastUtterance = null;

    public void addFirstPersonUtterance(CharSequence text, boolean focused) {
    	EditText edittext = (EditText) 
        	mInflater.inflate(R.layout.first_person_utterance, mContent, false);
        edittext.setText(text);
        edittext.setOnEditorActionListener(mEditorListener);
        edittext.setOnClickListener(mEditorListener);
        Bundle extras = edittext.getInputExtras(true);
        extras.putBoolean("show_language_toggle", false);
        mContent.addView(edittext);

        if (focused) {
	        edittext.requestFocus();
	        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	        imm.showSoftInput(edittext, InputMethodManager.SHOW_IMPLICIT);
        } else {
        	edittext.setFocusable(false);
        }

        post(new Runnable() {
            public void run() {
                fullScroll(FOCUS_DOWN);
            }
        });
        
        mLastUtterance = edittext;
    }

    @SuppressWarnings("deprecation")
	public CharSequence addSecondPersonUtterance(final CharSequence source, CharSequence target, final Object alternatives) {
    	TextView view;
    	if (mLastUtterance != null && mLastUtterance.getTag() != null)
    		view = (TextView) mLastUtterance.getTag();
    	else {
    		view = (TextView)
    			mInflater.inflate(R.layout.second_person_utterance, mContent, false);
    		if (mAlternativesListener != null)
        		view.setOnClickListener(mAlternativesListener);
        	mContent.addView(view);
            post(new Runnable() {
                public void run() {
                    fullScroll(FOCUS_DOWN);
                }
            });

    		mLastUtterance.setTag(view);
    	}

    	view.setTag(R.string.source_key, source);
    	view.setTag(R.string.target_key, target);
    	view.setTag(R.string.alternatives_key, alternatives);

    	// parse by words, marked by %, darkest red color
    	if (target.charAt(0) == '%') {
    		view.setBackgroundDrawable(getResources().getDrawable(R.drawable.second_person_worst_utterance_bg));
    		target = target.subSequence(2, target.length()) ;
    	}

    	// parse by chunks, marked by *, red color
    	else if (target.charAt(0) == '*') {
    		view.setBackgroundDrawable(getResources().getDrawable(R.drawable.second_person_chunk_utterance_bg));
    		target = target.subSequence(2, target.length()) ;
    	}

    	// parse error or unknown translations (in []) present, darkest red color
    	else if (target.toString().contains("parse error:") || target.toString().contains("[")) {
    		view.setBackgroundDrawable(getResources().getDrawable(R.drawable.second_person_worst_utterance_bg));
    	}

    	// parse by domain grammar, marked by +, green color
    	else if (target.charAt(0) == '+') {
    		view.setBackgroundDrawable(getResources().getDrawable(R.drawable.second_person_best_utterance_bg));
    		target = target.subSequence(2, target.length()) ;
    	}
    	
    	else {
    		view.setBackgroundDrawable(getResources().getDrawable(R.drawable.second_person_utterance_bg));
    	}

    	view.setText(target);
        return target;
    }

    public void updateLastUtterance(CharSequence text) {
    	if (mLastUtterance != null)
    		mLastUtterance.setText(text);
    }

    public void setOnAlternativesListener(final OnAlternativesListener listener) {
    	if (listener == null)
    		mAlternativesListener = null;
    	else
    		mAlternativesListener = new OnClickListener() {
	        	@Override
	        	public void onClick(View v) {
	        		String source = v.getTag(R.string.source_key).toString();
	        		Object alternatives = v.getTag(R.string.alternatives_key);
	        		listener.onAlternativesSelected(source, alternatives);
	        	}
	        };
    }
    
    public void setSpeechInputListener(ASR.Listener listener) {
    	mSpeechListener = listener;
    }
    
    public interface OnAlternativesListener {
    	public void onAlternativesSelected(CharSequence word, Object lexicon);
    }

    public void saveConversation(Bundle state) {
    	ArrayList<String> firstPersonUtterances   = new ArrayList<String>();
    	ArrayList<String> secondPersonUtterances  = new ArrayList<String>();
    	ArrayList<Object> translationAlternatives = new ArrayList<Object>();

    	int childCount = mContent.getChildCount();
    	for (int i = 0; i < childCount; i++) {
    		View child = mContent.getChildAt(i);
    		if (child.getClass() == TextView.class) {
    			firstPersonUtterances.add(child.getTag(R.string.source_key).toString());
    			secondPersonUtterances.add(child.getTag(R.string.target_key).toString());
    			translationAlternatives.add(child.getTag(R.string.alternatives_key));
    		}
    	}

    	state.putStringArrayList("first_person_uterances",  firstPersonUtterances);
		state.putStringArrayList("second_person_uterances", secondPersonUtterances);
		state.putSerializable("translation_alternatives",(Serializable) translationAlternatives);
    }

	public void restoreConversation(Bundle state) {
		final ArrayList<String> firstPersonUtterances  = state.getStringArrayList("first_person_uterances");
		final ArrayList<String> secondPersonUtterances = state.getStringArrayList("second_person_uterances");
		final ArrayList<Object> translationAlternatives= (ArrayList<Object>) state.getSerializable("translation_alternatives");

		post(new Runnable() {
			@Override
			public void run() {
				int i = 0;
				while (i < firstPersonUtterances.size() && 
					   i < Math.min(secondPersonUtterances.size(), translationAlternatives.size())) {
					String text = firstPersonUtterances.get(i);
					addFirstPersonUtterance(text, false);

					String translation  = secondPersonUtterances.get(i);
					Object alternatives = translationAlternatives.get(i);
					addSecondPersonUtterance(text, translation, alternatives);

					i++;
				}
			}
		});		
	}
}
