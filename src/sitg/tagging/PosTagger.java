package sitg.tagging;

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

/**
 * This class creates the Part of Speach (POS) tags of the input files.
 * It keeps only the nouns (N) and the adjectives (A) in the corresponding output files.
 * 
 * @author Fatemeh Borran
 */
public class PosTagger {
	
	public static void main(String args[]) throws IOException, ClassNotFoundException {
				
		// arguments: input output
		if (args.length != 2)
			System.out.println("Usage: java sitg.tagging.PosTagger input output");

		long start = System.currentTimeMillis();
		
		System.out.println("Tagging TXT files...");
		
		// Initialize the tagger		 
		MaxentTagger tagger = new MaxentTagger("taggers/french.tagger");
	    
		// French POS tags	
		TTags ttags = tagger.getTags();
		System.out.println("French POS tags: " + ttags);
		
		// input folder
		File input = new File(args[0]);
				
		// output folder
		File output = new File(args[1]);
		if (!output.exists())
			output.mkdir();				
		
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
		    
		    //if (results.split(" ").length >= 5) { // keep documents with at least five words
		    	BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output+"/"+file.getName()), "UTF8"));		    	
				writer.write(results);
				writer.close();
		    //}
		    
			reader.close();
		}
		
		System.out.println("All files are tagged with nouns and adjectives.");
				
		long end = System.currentTimeMillis();		
		
		System.out.println("Execution time: " + (end-start)/1000 + " seconds");
	}
	
}
