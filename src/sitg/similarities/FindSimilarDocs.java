package sitg.similarities;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import org.apache.lucene.analysis.Analyzer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;

import org.apache.mahout.common.HadoopUtil;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.StringTuple;
import org.apache.mahout.common.iterator.sequencefile.PathType;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileDirValueIterable;
import org.apache.mahout.vectorizer.DictionaryVectorizer;
import org.apache.mahout.vectorizer.DocumentProcessor;
import org.apache.mahout.vectorizer.tfidf.TFIDFConverter;

import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.text.SequenceFilesFromDirectory;

import sitg.MyAnalyzer;

public class FindSimilarDocs {

  public static void main(String args[]) throws Exception {
    	  
	// arguments: minSupport maxNGram minDF maxDFPercent
	if (args.length != 6)
		System.out.println("Usage: java sitg.similarities.FindSimilarDocs input output minSupport maxNGram minDF maxDFPercent");
	  
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);
    
    // inputDir contains the input text files
    String inputDir = args[0]; //"SITG_TXT/TITRE_RESUME_TAGGED";
   
    // delete exiting folders
    HadoopUtil.delete(conf, new Path(args[1]));
    HadoopUtil.delete(conf, new Path("temp"));
              
    // create the output folder
    new java.io.File(args[1]).mkdir();
    
    // seqDir contains the sequence files
    String seqDir = args[1]+"/seqDir";    
    // outputDir contains several intermediate folders
    String outputDir = args[1]+"/resultsDir";
    // matrixDir contains the matrix of documents
    String matrixDir = args[1]+"/matrixDir"; 
    // similarityDir contains the similarity matrix
    String similarityDir = args[1]+"/similarityDir";        

    ///////////////////////////////////////////////////////
    // 1. creating sequence files from text files
    ///////////////////////////////////////////////////////    
    SequenceFilesFromDirectory.main(new String[] {
            "--input", inputDir,
            "--output", seqDir,
            "--chunkSize", "64",
            "--charset", Charsets.UTF_8.name()
            });
    /*
    // 1.1. reading sequence files
    SequenceFile.Reader seq_reader = new SequenceFile.Reader(fs,
        new Path(seqDir + "/chunk-0"), conf);        
    // 1.2. writing sequence files
    Text seq_key = new Text();
    Text seq_value = new Text();        
    while (seq_reader.next(seq_key, seq_value)) {
    	System.out.println("key: " + seq_key + " value: " + seq_value.toString());
    }
    // 1.3. closing sequence reader
    seq_reader.close();
    */
    
    // custom analyzer
    MyAnalyzer analyzer = new MyAnalyzer();
    //System.out.println("Default stopwords: " + MyAnalyzer.stopwords);
    Set custom_stopwords = new HashSet(Arrays.asList("dime", "dim", "du", "dt", "ds", "dip", "df", "dcti", "dse", "dspe"));
    //custom_stopwords.addAll(new HashSet(Arrays.asList("département")));
    analyzer.addStopwords(custom_stopwords);
    //System.out.println("Custom stopwords: " + custom_stopwords);

    ///////////////////////////////////////////////////////    
    // 2. tokenizing the text files using custom analyzer
    ///////////////////////////////////////////////////////    
    Path tokenizedPath = new Path(outputDir,
        DocumentProcessor.TOKENIZED_DOCUMENT_OUTPUT_FOLDER);
    DocumentProcessor.tokenizeDocuments(new Path(seqDir), analyzer.getClass()
        .asSubclass(Analyzer.class), tokenizedPath, conf);

    // 2.1. reading tokenized documents
    SequenceFile.Reader token_reader = new SequenceFile.Reader(fs,
        new Path(tokenizedPath + "/part-m-00000"), conf);    
    // 2.2. writing tokenized documents into a file 
    PrintWriter token_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/tokens.txt"), "UTF8")));
    // 2.3. creating a hashtable of tokens
    Hashtable<String, List<String>> tokens = new Hashtable<String, List<String>>();
    Text token_key = new Text();
    StringTuple token_value = new StringTuple();        
    while (token_reader.next(token_key, token_value)) {
    	tokens.put(token_key.toString(), token_value.getEntries());
    	token_out.println(token_key + ": " + token_value);
    }
    // 2.4. closing token readers
    token_out.close();
    token_reader.close();
    
    ///////////////////////////////////////////////////////    
    // 3. creating term frequency and document frequency vectors
    // minSupport: the minimum frequency of the feature in the entire corpus 
    // to be considered for inclusion in the sparse vector (it cuts from clusterDir/wordcount and not frequency)
    ///////////////////////////////////////////////////////    
    int minSupport = Integer.parseInt(args[2]); // [0-10] default:0
    int maxNGramSize = Integer.parseInt(args[3]); // [1-5] default:1
    int minLLRValue = 50; //? [0,10,50,100,1000] default:50
    float normPower = -1.0f; //? only -1.0f works?!
    boolean logNormalize = true; // default: true   
    int reduceTasks = 1;
    int chunkSize = 200;
    boolean sequentialAccessOutput = true;
    boolean namedVectors = true;
    DictionaryVectorizer.createTermFrequencyVectors(tokenizedPath,
      new Path(outputDir), DictionaryVectorizer.DOCUMENT_VECTOR_OUTPUT_FOLDER, 
      conf, minSupport, maxNGramSize, minLLRValue, normPower, logNormalize, reduceTasks,
      chunkSize, sequentialAccessOutput, namedVectors);

    // 3.1. reading dictionary file
    SequenceFile.Reader dictionary_reader = new SequenceFile.Reader(fs,
        new Path(outputDir + "/dictionary.file-0"), conf);

    // 3.2. creating a hashtable of dictionary
    Hashtable<Integer, String> dictionary = new Hashtable<Integer, String>();
    // 3.3. writing the dictionary into a file
    PrintWriter dictionary_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/dictionary.txt"), "UTF8")));
    Text dictionary_key = new Text();
    IntWritable dictionary_value = new IntWritable();        
    while (dictionary_reader.next(dictionary_key, dictionary_value)) {    	
    	dictionary.put(dictionary_value.get(), dictionary_key.toString());
    	dictionary_out.println(dictionary_value + ": " + dictionary_key);
    }
    // 3.4. closing dictionary readers
    dictionary_out.close();
    dictionary_reader.close();    
    
    // 3.5. reading term frequency
    List<Vector> tf_vectors = Lists.newArrayList();
    for (VectorWritable vectorWritable :
         new SequenceFileDirValueIterable<VectorWritable>(new Path(outputDir + "/tf-vectors"), 
        		 PathType.LIST, conf)) {
      Vector tf_vector = vectorWritable.get();
	  tf_vectors.add(tf_vector);
    }
    // 3.6. writing term frequency into a file 
    PrintWriter tf_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/tf_vector.txt"), "UTF8")));  
    for (Vector vector : tf_vectors) {
    	String name = vector.toString();
    	tf_out.print(name.substring(0, name.indexOf(':')) + " = {");
    	Iterator<Element> elements = vector.iterateNonZero();
    	while (elements.hasNext()) {
    		Element elem = elements.next();
        	tf_out.print(dictionary.get(elem.index()) + ": " + elem.get() + ", ");
    	}
    	tf_out.println("}");
    }
    // 3.7. close tf file
    tf_out.close();

    ///////////////////////////////////////////////////////    
    // 4. creating document frequency 
    ///////////////////////////////////////////////////////    
    Pair<Long[], List<Path>> dfData = TFIDFConverter.calculateDF(
    		new Path(outputDir, DictionaryVectorizer.DOCUMENT_VECTOR_OUTPUT_FOLDER),
    	    new Path(outputDir), conf, chunkSize);

    // 4.1. reading frequency file
    SequenceFile.Reader frequency_reader = new SequenceFile.Reader(fs,
        new Path(outputDir + "/frequency.file-0"), conf);
        
    // 4.2. creating a hashtable of frequency
    Hashtable<Integer, Long> frequency = new Hashtable<Integer, Long>();
    // 4.3. writing frequency into a file
    PrintWriter frequency_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/frequency.txt"), "UTF8")));
    IntWritable frequency_key = new IntWritable();
    LongWritable frequency_value = new LongWritable();        
    while (frequency_reader.next(frequency_key, frequency_value)) {    	
    	frequency.put(frequency_key.get(), frequency_value.get());
    	frequency_out.println(dictionary.get(frequency_key.get()) + ": " + frequency_value.get());
    }
    // 4.4. close frequency file    
    frequency_out.close();
    frequency_reader.close();
    // 4.5. sorting frequency file
	ArrayList<Map.Entry<Integer, Long>> frequencyList = new ArrayList<Map.Entry<Integer, Long>>(frequency.entrySet());
	Collections.sort(frequencyList, new Comparator<Map.Entry<Integer, Long>>() {

		@Override
		public int compare(Map.Entry<Integer, Long> o1, Map.Entry<Integer, Long> o2) {
			return o2.getValue().compareTo(o1.getValue());
		}
		
	});
    // 4.6. writing sorted frequency into a file 
    frequency_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/frequency_sorted.txt"), "UTF8")));
    for (int i = 0; i < frequencyList.size(); i++) {
    	Map.Entry<Integer, Long> entry = frequencyList.get(i);
    	long freq = (Long) entry.getValue();
    	frequency_out.println(dictionary.get(entry.getKey()) + ": " + freq);
    }    
    // 4.7. close frequency file    
    frequency_out.close();
     
    ///////////////////////////////////////////////////////    
    // 5. creating TFIDF  
    ///////////////////////////////////////////////////////    
    int minDf = Integer.parseInt(args[4]); // [0-9] default:0
    int maxDFPercent = Integer.parseInt(args[5]); // [90, 95, 100] default:100
    float norm = 2; // [0-3] default:2 (must be > 1)    
    TFIDFConverter.processTfIdf(
      new Path(outputDir , DictionaryVectorizer.DOCUMENT_VECTOR_OUTPUT_FOLDER),
      new Path(outputDir), conf, dfData, minDf,
      maxDFPercent, norm, logNormalize, sequentialAccessOutput, namedVectors, reduceTasks);
    
    Path vectorsFolder = new Path(outputDir, "tfidf-vectors");
    
    // 5.1. reading tfidf
    List<Vector> tfidf_vectors = Lists.newArrayList();
    for (VectorWritable vectorWritable :
         new SequenceFileDirValueIterable<VectorWritable>(vectorsFolder, 
        		 PathType.LIST, conf)) {
      Vector tfidf_vector = vectorWritable.get();
	  tfidf_vectors.add(tfidf_vector);
    }
    // 5.2. writing tfidf vector into a file 
    PrintWriter tfidf_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/tfidf_vector.txt"), "UTF8")));    
    DecimalFormat df = new DecimalFormat("#.##");
    Hashtable<String, Hashtable<String, Double>> sim_vectors = new Hashtable<String, Hashtable<String, Double>>();
    for (Vector vector : tfidf_vectors) {
    	String name = vector.toString();
    	name = name.substring(0, name.indexOf(':'));
    	tfidf_out.print(name + " = {");
    	Iterator<Element> elements = vector.iterateNonZero();
    	Hashtable<String, Double> sim_vector = new Hashtable<String, Double>();
    	while (elements.hasNext()) {
    		Element elem = elements.next();
    		sim_vector.put(dictionary.get(elem.index()), Double.valueOf(df.format(elem.get())));
        	tfidf_out.print(dictionary.get(elem.index()) + ": " + df.format(elem.get()) + ", ");
    	}    	
    	sim_vectors.put(name, sim_vector);
    	tfidf_out.println("}");
    }
    // 5.3. close tfidf file      
    tfidf_out.close();
    		
    ///////////////////////////////////////////////////////    
    // 6. creating matrix from vectors
    ///////////////////////////////////////////////////////       
    org.apache.mahout.utils.vectors.RowIdJob.main(new String[] {
            "--input", vectorsFolder.toString(),
            "--output", matrixDir
            });

    // 6.1. reading docIndex    
    SequenceFile.Reader docIndex_reader = new SequenceFile.Reader(fs,
        new Path(matrixDir + "/docIndex"), conf);        
    // 6.2. storing docIndex into a hashtable
    IntWritable docIndex_key = new IntWritable();
    Text docIndex_value = new Text();
    Hashtable<Integer, String> docIndex = new Hashtable<Integer, String>(); 
    while (docIndex_reader.next(docIndex_key, docIndex_value)) {
    	//System.out.println("key: " + docIndex_key + " value: " + docIndex_value.toString());
    	docIndex.put(docIndex_key.get(), docIndex_value.toString());
    }    
    // 6.3. closing docIndex reader
    docIndex_reader.close();
    
    ///////////////////////////////////////////////////////    
    // 7. creating similarity from matrix
    ///////////////////////////////////////////////////////     
    int numOfColumns = docIndex.size(); 
    int numOfSims = 10;
    System.out.println("numberOfColumns: " + numOfColumns);
    org.apache.mahout.math.hadoop.similarity.cooccurrence.RowSimilarityJob.main(new String[] {
            "--input", matrixDir + "/matrix",
            "--output", similarityDir,
            "--numberOfColumns", String.valueOf(numOfColumns),
            "--similarityClassname", "SIMILARITY_COSINE",
            "--maxSimilaritiesPerRow", String.valueOf(numOfSims),
            "-ess", "true"
            });
    
    // 7.1. reading similarity vectors    
    List<Vector> similarity_vectors = Lists.newArrayList();
    for (VectorWritable vectorWritable :
         new SequenceFileDirValueIterable<VectorWritable>(new Path(similarityDir), 
        		 PathType.LIST, conf)) {
      Vector similarity_vector = vectorWritable.get();
      similarity_vectors.add(similarity_vector);
    }
    System.out.println("size of similarity vectors: " + similarity_vectors.size()); 
    
    // 7.2. writing similarity vectors into a file
    PrintWriter similarity_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/similartiy_vector.txt"), "UTF8")));
    // 7.3. generating SKOS format 
    PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/similar.xml"), "UTF8")));
	writer.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.");
	writer.println("@prefix skos: <http://www.w3.org/2004/02/skos/core#>.");
	writer.println("@prefix sitg: <http://www.sitg.ch/>.");
	writer.println();
	
    int sim_key = 0;
    for (Vector vector : similarity_vectors) {
    	String name = docIndex.get(sim_key);
    	Hashtable<String, Double> sims = sim_vectors.get(name);
    	ArrayList<Map.Entry<String, Double>> simsList = new ArrayList<Map.Entry<String, Double>>(sims.entrySet());
    	Collections.sort(simsList, new Comparator<Map.Entry<String, Double>>() {
    		@Override
    		public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {    			
    			return o2.getValue().compareTo(o1.getValue());
    		}
    		
    	});   	   	
    	name = name.substring(1, name.length());
    	name = name.replaceAll(".txt", ".xml");
    	similarity_out.println(name + " : ");
    	similarity_out.println("   " + simsList.subList(0, Math.min(simsList.size(), numOfSims)));
    	//similarity_out.println(sim_key + " : ");
    	writer.println("sitg:" + name + " rdf:type skos:Concept;");   
       	writer.println("    skos:definition \"" + simsList.subList(0, Math.min(simsList.size(), numOfSims)) + "\".");
    	Iterator<Element> elements = vector.iterateNonZero();
    	Hashtable<String, Double> similarity = new Hashtable<String, Double>(); 
    	while (elements.hasNext()) {
    		Element elem = elements.next();
    		name = docIndex.get(elem.index());
    		name = name.substring(1, name.length());
    		name = name.replaceAll(".txt", ".xml");
    		Double value = Double.valueOf(df.format(elem.get()));
    		similarity.put(name, value);
    	}
    	ArrayList<Map.Entry<String, Double>> similarityList = new ArrayList<Map.Entry<String, Double>>(similarity.entrySet());
    	Collections.sort(similarityList, new Comparator<Map.Entry<String, Double>>() {
    		@Override
    		public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {    			
    			return o2.getValue().compareTo(o1.getValue());
    		}
    		
    	});
        for (int i = 0; i < similarityList.size(); i++) {
        	Map.Entry<String, Double> entry = similarityList.get(i);
        	double sim = (Double) entry.getValue();        	
        	similarity_out.println("   " + entry.getKey() + ": " + sim);
        	writer.println("    skos:related sitg:" + entry.getKey() + ";"); 
        }      	    	    
    	similarity_out.println();
       	writer.println("    skos:note \"" + similarityList.subList(0, Math.min(similarityList.size(), numOfSims)) + "\".");
       	writer.println();    	
    	sim_key++;
    }
    // 7.4. closing similarity file
    similarity_out.close();
    writer.close();
    
    HadoopUtil.delete(conf, new Path("temp"));
        
  }
}
