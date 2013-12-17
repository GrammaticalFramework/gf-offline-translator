package org.grammaticalframework.ui.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.Log;

import org.grammaticalframework.pgf.Concr;
import org.grammaticalframework.pgf.Expr;
import org.grammaticalframework.pgf.MorphoAnalysis;
import org.grammaticalframework.pgf.PGF;
import org.grammaticalframework.pgf.ParseError;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Translator {

    private static final String TAG = "Translator";

    // TODO: allow changing
    private String mGrammar = "Parse8.pgf";

    // TODO: build dynamically?
    private Language[] mLanguages = {

	new Language("en-US", "English", "ParseEng", R.xml.inflection_en, R.xml.qwerty),
        new Language("bg-BG", "Bulgarian", "ParseBul", R.xml.inflection_bg, R.xml.cyrillic),
        new Language("cmn-Hans-CN", "Chinese", "ParseChi", R.xml.inflection_cmn, R.xml.qwerty),   
        new Language("fi-FI", "Finnish", "ParseFin", R.xml.inflection_fi, R.xml.qwerty),
	new Language("fr-FR", "French", "ParseFre", R.xml.inflection_fr, R.xml.qwerty),  
	new Language("de-DE", "German", "ParseGer", 0, R.xml.qwerty), 
	new Language("hi-IN", "Hindi", "ParseHin", 0, R.xml.qwerty), /// 
        new Language("sv-SE", "Swedish", "ParseSwe", R.xml.inflection_sv, R.xml.qwerty), 
    };

    private Context mContext;

	private GrammarLoader mGrammarLoader;
    private ConcrLoader mSourceLoader;
    private ConcrLoader mTargetLoader;
    private ConcrLoader mOtherLoader;

	private static final String SOURCE_LANG_KEY = "source_lang";
	private static final String TARGET_LANG_KEY = "target_lang";
	
	private SharedPreferences mSharedPref;
	
	private Language getPrefLang(String key, int def) {
		int index = mSharedPref.getInt(key, def);
		if (index < 0 || index >= mLanguages.length)
			index = def;
		return mLanguages[index];
	}

	private void setPrefLang(String key, Language def) {
		for (int index = 0; index < mLanguages.length; index++) {
			if (def == mLanguages[index]) {
				SharedPreferences.Editor editor = mSharedPref.edit();
				editor.putInt(key, index);
				editor.commit();
				break;
			}
		}
	}

    public Translator(Context context) {
    	mContext = context;

		mSharedPref = context.getSharedPreferences(
				context.getString(R.string.global_preferences_key), Context.MODE_PRIVATE);

		mGrammarLoader = new GrammarLoader();
		mGrammarLoader.start();
		
		Language prefSourceLang = getPrefLang(SOURCE_LANG_KEY, 0);
		Language prefTargetLang = getPrefLang(TARGET_LANG_KEY, 1);
		
        mSourceLoader = new ConcrLoader(prefSourceLang);
        mSourceLoader.start();
        
        if (prefSourceLang == prefTargetLang) {
        	mTargetLoader = mSourceLoader;
        } else {
        	mTargetLoader = new ConcrLoader(prefTargetLang);
        	mTargetLoader.start();
        }

        mOtherLoader = null;
    }

    public List<Language> getAvailableLanguages() {
        return Arrays.asList(mLanguages);
    }

    public Language getSourceLanguage() {
        return mSourceLoader.getLanguage();
    }

    public void setSourceLanguage(Language language) {
    	setPrefLang(SOURCE_LANG_KEY, language);

    	if (mSourceLoader.getLanguage() == language)
    		return;
    	if (mTargetLoader.getLanguage() == language) {
    		cacheOrUnloadLanguage(mSourceLoader);
    		mSourceLoader = mTargetLoader;
    		return;
    	}
    	if (mOtherLoader != null &&
    	    mOtherLoader.getLanguage() == language) {
    		ConcrLoader tmp = mSourceLoader;
    		mSourceLoader = mOtherLoader;
    		mOtherLoader  = tmp;
    		return;
    	}

    	try {
    		mSourceLoader.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}

    	if (mSourceLoader.getLanguage() != mTargetLoader.getLanguage()) {
    		cacheOrUnloadLanguage(mSourceLoader);
    	}

        mSourceLoader = new ConcrLoader(language);
        mSourceLoader.start();
    }

    public boolean isSourceLanguageLoaded() {
    	try {
    		mSourceLoader.join();
    		return true;
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}
    	return false;
    }

    private Concr getSourceConcr() {
    	try {
    		mSourceLoader.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}
        return mSourceLoader.getConcr();
    }

    public Language getTargetLanguage() {
        return mTargetLoader.getLanguage();
    }

    public void setTargetLanguage(Language language) {
    	setPrefLang(TARGET_LANG_KEY, language);

    	if (mSourceLoader.getLanguage() == language) {
    		cacheOrUnloadLanguage(mTargetLoader);
    		mTargetLoader = mSourceLoader;
    		return;
    	}
    	if (mTargetLoader.getLanguage() == language)
    		return;
    	if (mOtherLoader != null &&
    	    mOtherLoader.getLanguage() == language) {
    		ConcrLoader tmp = mTargetLoader;
    		mTargetLoader = mOtherLoader;
    		mOtherLoader  = tmp;
    		return;
    	}

    	try {
    		mTargetLoader.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}

    	if (mSourceLoader.getLanguage() != mTargetLoader.getLanguage()) {
    		cacheOrUnloadLanguage(mTargetLoader);
    	}

    	mTargetLoader = new ConcrLoader(language);
    	mTargetLoader.start();
    }

    public boolean isTargetLanguageLoaded() {
    	try {
    		mTargetLoader.join();
    		return true;
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}
    	return false;
    }

    private Concr getTargetConcr() {
    	try {
    		mTargetLoader.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}
        return mTargetLoader.getConcr();
    }

	private void cacheOrUnloadLanguage(ConcrLoader loader) {
		if (mOtherLoader != null) {
			mOtherLoader.getConcr().unload();
			Log.d(TAG, mOtherLoader.getLanguage().getConcrete() + ".pgf_c unloaded");
		}
		mOtherLoader = loader;
	}

    public void switchLanguages() {
    	ConcrLoader tmp = mSourceLoader;
    	mSourceLoader = mTargetLoader;
    	mTargetLoader = tmp;
    }

    private static String explode(String in) {
    	String out = "";
    	for (int i = 0; i < in.length(); i++) {
    		if (i > 0)
    			out += ' ';
    		out += in.charAt(i);
    	}
    	return out;
    }
    /**
     * Takes a lot of time. Must not be called on the main thread.
     */
    public String translate(String input) {
        if (getSourceLanguage().getLangCode().equals("cmn-Hans-CN")) {
        	// for Chinese we need to put space after every character
        	input = explode(input);
        }

        try {
            Concr sourceLang = getSourceConcr();
	    Expr expr = sourceLang.parseBest(getGrammar().getStartCat(), input);
            Concr targetLang = getTargetConcr();
            String output = targetLang.linearize(expr);
            return output;
        } catch (ParseError e) {
            Log.e(TAG, "Parse error: " + e);
            return "parse error: " + e.getMessage();
        }
    }

    private String getLemmaTag(String lemma) {
    	String cat = getGrammar().getFunctionType(lemma).getCategory();
    	
		int res = getTargetLanguage().getInflectionResource();
		if (res == 0)
			return "";

		XmlResourceParser parser = mContext.getResources().getXml(res);

		try {
			int state = 0;
			int event = parser.next();
			String tag = null;
			boolean found = false;
			while (event != XmlResourceParser.END_DOCUMENT) {
				switch (event) {
				case XmlResourceParser.START_TAG:
					if (state == 0 && "inflection".equals(parser.getName())) {
						state = 1;
						tag   = null;
						found = false;
					} else if (state == 1 && "cat".equals(parser.getName())) {
						state = 2;
					} else if (state == 1 && "tag".equals(parser.getName())) {
						state = 3;
					} else if (state == 1 && "template".equals(parser.getName())) {
						state = 4;
					}
					break;
				case XmlResourceParser.END_TAG:
					if (state == 1 && "inflection".equals(parser.getName())) {
						state = 0;
						if (found)
							return tag+".";
					} else if (state == 2 && "cat".equals(parser.getName())) {
						state = 1;
					} else if (state == 3 && "tag".equals(parser.getName())) {
						state = 1;
					} else if (state == 4 && "template".equals(parser.getName())) {
						state = 1;
					}
					break;
				case XmlResourceParser.TEXT:
					if (state == 2) {
						if (cat.equals(parser.getText())) {
							found = true;
						}
					} else if (state == 3) {
						tag = parser.getText();
					}
					break;
				}
				event = parser.next();
			}
		} catch (IOException e) {
			Log.e(TAG, "getLemmaTag", e);
		} catch (XmlPullParserException e) {
			Log.e(TAG, "getLemmaTag", e);
		} finally {
			parser.close();
		}
		
		return "";
    }

    public String generateLexiconEntry(String lemma) {
    	Expr e = Expr.readExpr(lemma);
        Concr sourceLang = getSourceConcr();
        Concr targetLang = getTargetConcr();
        if (targetLang.hasLinearization(lemma))
        	return sourceLang.linearize(e) + " - " + getLemmaTag(lemma) + " " + targetLang.linearize(e);
        else
        	return sourceLang.linearize(e) + " " + getLemmaTag(lemma);        
    }

	public String getInflectionTable(String lemma) {
		Concr targetLang = getTargetConcr();
		
		if (!targetLang.hasLinearization(lemma))
			return null;

		int res = getTargetLanguage().getInflectionResource();
		if (res == 0)
			return "";

		Map<String,Map<String,String>> cache = new HashMap<String,Map<String,String>>();

		String cat = getGrammar().getFunctionType(lemma).getCategory();

		XmlResourceParser parser = mContext.getResources().getXml(res);
		StringBuilder builder = new StringBuilder();
		builder.append("<html><head><meta charset=\"UTF-8\"/></head><body>");

		try {
			int state = 0;
			int event = parser.next();
			boolean emit = false;
			boolean form = false;
			boolean lin  = false;
			String formName = null;
			StringBuilder abstrBuilder = null;
			while (event != XmlResourceParser.END_DOCUMENT) {
				switch (event) {
				case XmlResourceParser.START_TAG:
					if (state == 0 && "inflection".equals(parser.getName())) {
						state = 1;
					} else if (state == 1 && "cat".equals(parser.getName())) {
						state = 2;
					} else if (state == 1 && "template".equals(parser.getName())) {
						state = 4;
					} else if (state == 4 && "form".equals(parser.getName())) {
						form = true;
					} else if (state == 4 && emit && "lin".equals(parser.getName())) {
						lin = true;
						emit = false;
						abstrBuilder = new StringBuilder();
						formName = parser.getAttributeValue(null, "form");
					} else if (state == 4 && lin && "cat".equals(parser.getName())) {
						abstrBuilder.append(cat);
					} else if (state == 4 && lin && "lemma".equals(parser.getName())) {
						abstrBuilder.append(lemma);
					} else if (state == 4 && emit) {
						builder.append("<"+parser.getName());
						int n_attrs = parser.getAttributeCount();
						for (int i = 0; i < n_attrs; i++) {
							builder.append(' ');
							builder.append(parser.getAttributeName(i));
							builder.append("=\"");
							builder.append(parser.getAttributeValue(i));
							builder.append("\"");
						}
						builder.append(">");
					}
					break;
				case XmlResourceParser.END_TAG:
					if (state == 1 && "inflection".equals(parser.getName())) {
						state = 0;
					} else if (state == 2 && "cat".equals(parser.getName())) {
						state = 1;
					} else if (state == 4 && "template".equals(parser.getName())) {
						state = 1;
						emit = false;
					} else if (state == 4 && "form".equals(parser.getName())) {
						form = false;
					} else if (state == 4 && lin && "lin".equals(parser.getName())) {
						String s = abstrBuilder.toString();
						if (formName == null) {
							Expr expr = Expr.readExpr(s);
							builder.append(TextUtils.htmlEncode(targetLang.linearize(expr)));
						} else {
							Map<String,String> elins = cache.get(s);
							if (elins == null) {
								Expr expr = Expr.readExpr(s);
								elins = targetLang.tabularLinearize(expr);
								cache.put(s, elins);
							}
							String elin = elins.get(formName);
							builder.append(TextUtils.htmlEncode(elin));
						}

						lin  = false;
						emit = true;
					} else if (state == 4 && emit) {
						builder.append("</"+parser.getName()+">");
					}
					break;
				case XmlResourceParser.TEXT:
					if (state == 2) {
						if (cat.equals(parser.getText()))
							emit = true;
					} else if (state == 4 && emit) {
						if (form) {
							Map<String,String> elins = cache.get(lemma);
							if (elins == null) {
								Expr expr = Expr.readExpr(lemma);
								elins = targetLang.tabularLinearize(expr);
								cache.put(lemma, elins);
							}
							String s = elins.get(parser.getText());
							if (s != null)
								builder.append(TextUtils.htmlEncode(s));
						} else {
							builder.append(parser.getText());
						}
					} else if (state == 4 && lin) {
						abstrBuilder.append(parser.getText());
					}
					break;
				}
				event = parser.next();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} finally {
			parser.close();
		}
		
		builder.append("</body>");

		return builder.toString();
	}

    public List<MorphoAnalysis> lookupMorpho(String sentence) {
    	return getSourceConcr().lookupMorpho(sentence);
    }

	private PGF getGrammar() {
		try {
			mGrammarLoader.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Loading interrupted", e);
		}
		return mGrammarLoader.getGrammar();
	}

	private class GrammarLoader extends Thread {
		private PGF mPGF;
		
		public GrammarLoader() {
			mPGF = null;
		}

		public PGF getGrammar() {
			return mPGF;
		}

		public void run() {
			InputStream in = null;
			
		    try {
		    	in = mContext.getAssets().open(mGrammar);
		        Log.d(TAG, "Trying to open " + mGrammar);
		        long t1 = System.currentTimeMillis();
		        mPGF = PGF.readPGF(in);
		        long t2 = System.currentTimeMillis();
		        Log.d(TAG, mGrammar + " loaded ("+(t2-t1)+" ms)");		        
		    } catch (FileNotFoundException e) {
		        Log.e(TAG, "File not found", e);
		    } catch (IOException e) {
		        Log.e(TAG, "Error loading grammar", e);
		    } finally {
		    	if (in != null) {
		    		try {
		    			in.close();
		    		} catch (IOException e) {
		    			Log.e(TAG, "Error closing the stream", e);
		    		}
		    	}
		    }
		}
	}

	private class ConcrLoader extends Thread {
		private Language mLanguage;
		private Concr mConcr;

		public ConcrLoader(Language lang) {
			this.mLanguage = lang;
			this.mConcr = null;
		}

		public Language getLanguage() {
			return mLanguage;
		}
		
		public Concr getConcr() {
			return mConcr;
		}

		public void run() {
			try {
				mGrammarLoader.join();
			} catch (InterruptedException e) {
				Log.d(TAG, "interrupted", e);
			}

			InputStream in = null;

		    try {
		    	String name = mLanguage.getConcrete()+".pgf_c";
		    	in = mContext.getAssets().open(name);
		        Log.d(TAG, "Trying to load " + name);
		        long t1 = System.currentTimeMillis();
		        mConcr = mGrammarLoader.getGrammar().getLanguages().get(mLanguage.getConcrete());
		        mConcr.load(in);
		        long t2 = System.currentTimeMillis();
		        Log.d(TAG, name + " loaded ("+(t2-t1)+" ms)");
		    } catch (FileNotFoundException e) {
		        Log.e(TAG, "File not found", e);
		    } catch (IOException e) {
		        Log.e(TAG, "Error loading concrete", e);
		    } finally {
		    	if (in != null) {
		    		try {
		    			in.close();
		    		} catch (IOException e) {
		    			Log.e(TAG, "Error closing the stream", e);
		    		}
		    	}
		    }
		}
	}
}
