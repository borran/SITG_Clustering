package sitg;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LengthFilter;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.fr.FrenchLightStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;

public class MyAnalyzer extends Analyzer {
	
	static Set stopwords;	
	
	public MyAnalyzer() {
		stopwords = FrenchAnalyzer.getDefaultStopSet();
	}

	public void addStopwords(Set stops) {
		stopwords.addAll(stops);
	}
	
	private final Pattern separators = Pattern.compile("[\\p{Punct}]");
	private final Pattern digits = Pattern.compile("[\\d]");
	
	@Override
	public TokenStream tokenStream(String fieldName, Reader reader) {		
		TokenStream result = new WhitespaceTokenizer(Version.LUCENE_36, reader);
		result = new LowerCaseFilter(Version.LUCENE_36, result);
		
	    CharTermAttribute termAtt = (CharTermAttribute) result.addAttribute(CharTermAttribute.class);
	    StringBuilder buf = new StringBuilder();
	    try {
	      while (result.incrementToken()) {	        
	        String word = new String(termAtt.buffer(), 0, termAtt.length());
	        word = word.replaceAll("¨", "?");
	        word = word.replaceAll("Õ", "'");
	        String[] words = separators.split(word);
	        for (String w : words)
	        	buf.append(w).append(" ");		       
	      }
	    } catch (IOException e) {
	      e.printStackTrace();
	    }

	    result = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(buf.toString()));
		
	    termAtt = (CharTermAttribute) result.addAttribute(CharTermAttribute.class);
	    buf = new StringBuilder();
	    try {
	      while (result.incrementToken()) {	        
	        String word = new String(termAtt.buffer(), 0, termAtt.length());
	        String[] words = digits.split(word);
	        for (String w : words)
	        	buf.append(w).append(" ");	       
	      }
	    } catch (IOException e) {
	      e.printStackTrace();
	    }
	    
	    result = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(buf.toString()));
	    
		result = new StopFilter(Version.LUCENE_36, result, stopwords);	    
		
	    termAtt = (CharTermAttribute) result.addAttribute(CharTermAttribute.class);
	    buf = new StringBuilder();
	    try {
	      while (result.incrementToken()) {	        
	        String word = new String(termAtt.buffer(), 0, termAtt.length());
	        if (word.length() > 2)
	        	buf.append(word).append(" ");	       
	      }
	    } catch (IOException e) {
	      e.printStackTrace();
	    }
	    
	    result = new WhitespaceTokenizer(Version.LUCENE_36, new StringReader(buf.toString()));
	    
		//result = new FrenchLightStemFilter(result);
		return result;
	}
 
}