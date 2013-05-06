package sitg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TTags;

public class TestTagger {
	
	public static void main(String args[]) throws IOException, ClassNotFoundException {
		
		// Initialize the tagger		 
		MaxentTagger tagger = new MaxentTagger("taggers/french.tagger");
	    
		// French POS tags	
		TTags ttags = tagger.getTags();
		System.out.println("French POS tags: " + ttags);
		
		// output folder
		File output = new File("nounAdjDir");
		if (!output.exists())
			output.mkdir();				
		
		// input folder
		File input = new File("inputDir");
		for (File file : input.listFiles()) {
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));					
											
		    DocumentPreprocessor dp = new DocumentPreprocessor(reader);
		    dp.setTokenizerFactory(PTBTokenizerFactory.newWordTokenizerFactory("americanize=false,unicodeQuotes=true,unicodeEllipsis=true,ptb3Escaping=false"));
		    String results = "";
		    for (List<HasWord> sentence : dp) {
		    	List<TaggedWord> tSentence = tagger.tagSentence(sentence);	
		    	for (TaggedWord tWord: tSentence) {
		    		//writer.write(tWord.toString() + " ");
		    		String word = tWord.value();		    		
		    		String tag = tWord.tag();
		    		/*
		    		if (!tag.equals("V")) // remove verbs
		    			writer.write(word + " ");
		    		*/		 
		    		/*
		    		if (tag.equals("N")) // keep only nouns
		    			writer.write(word + " ");
		    		*/		    			
		    		
		    		if (tag.equals("N") || tag.equals("A")) // keep nouns and adjectives
		    			results += word + " ";

		    			
		    	}		    	
		    }
		    
		    //if (results.split(" ").length >= 5) {
		    	BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output+"/"+file.getName()), "UTF8"));		    	
				writer.write(results);
				writer.close();
		    //}
		    
			reader.close();
		}
		
	}
	
}
