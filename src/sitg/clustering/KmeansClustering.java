package sitg.clustering;

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

import org.apache.mahout.clustering.Cluster;
import org.apache.mahout.clustering.classify.WeightedVectorWritable;
import org.apache.mahout.clustering.canopy.CanopyDriver;
import org.apache.mahout.clustering.evaluation.ClusterEvaluator;
import org.apache.mahout.clustering.iterator.ClusterWritable;
import org.apache.mahout.clustering.kmeans.KMeansDriver;
import org.apache.mahout.clustering.kmeans.RandomSeedGenerator;
import org.apache.mahout.common.HadoopUtil;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.common.StringTuple;
import org.apache.mahout.common.distance.CosineDistanceMeasure;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.common.iterator.sequencefile.PathFilters;
import org.apache.mahout.common.iterator.sequencefile.PathType;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileDirValueIterable;
import org.apache.mahout.vectorizer.DictionaryVectorizer;
import org.apache.mahout.vectorizer.DocumentProcessor;
import org.apache.mahout.vectorizer.tfidf.TFIDFConverter;

import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.math.VectorWritable;
import org.apache.mahout.text.SequenceFilesFromDirectory;
import org.apache.mahout.utils.SequenceFileDumper;
import org.apache.mahout.utils.clustering.ClusterDumper;

import sitg.MyAnalyzer;

/**
 * This file uses Kmeans Clustering on the SITG Catalog.
 * 
 * @author Fatemeh Borran
 */
public class KmeansClustering {

  public static void main(String args[]) throws Exception {
    	  
	// arguments: k maxNGram minSupport minDF maxDFPercent
	if (args.length != 7)
		System.out.println("Usage: java sitg.clustering.KmeansClustering input output k maxNGram minSupport minDF maxDFPercent");
	  
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.get(conf);
    	  
    // inputDir contains the text files
    String inputDir = args[0]; //"SITG_TXT/TITRE_RESUME_TAGGED";

    // delete exiting folders
    HadoopUtil.delete(conf, new Path(args[1]));    
    
    // create the output folder
    new java.io.File(args[1]).mkdir();
    
    // seqDir contains the sequence files
    String seqDir = args[1]+"/seqDir";    
    
    ///////////////////////////////////////////////////////
    // 1. creating sequence files from text files
    ///////////////////////////////////////////////////////   
    SequenceFilesFromDirectory.main(new String[] {
            "--input", inputDir,
            "--output", seqDir,
            "--chunkSize", "64",
            "--charset", Charsets.UTF_8.name()
            });
	
    
    // clusterDir contains several folders created during clustering
    String outputDir = args[1]+"/clusterDir";
    /*
    // 1.1. reading sequence files
    SequenceFile.Reader seq_reader = new SequenceFile.Reader(fs,
        new Path(seqDir + "/chunk-0"), conf);
    // 1.2. writing sequence files    
    Text seq_key = new Text();
    Text seq_value = new Text();    
    
    while (seq_reader.next(seq_key, seq_value)) {
    	System.out.println("clé: " + seq_key + " valeur: " + seq_value.toString());
    }
    // 1.3. closing sequence reader    
    seq_reader.close();
    */
    
    HadoopUtil.delete(conf, new Path(outputDir));
    
    // custom analyzer
    MyAnalyzer analyzer = new MyAnalyzer();
    //System.out.println("Default stopwords: " + MyAnalyzer.stopwords);
    Set custom_stopwords = new HashSet(Arrays.asList("dime", "dim", "du", "dt", "ds", "dip", "df", "dcti", "dse", "dspe"));
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
    int minSupport = Integer.parseInt(args[4]); // [0-10] default:0
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
	ArrayList frequencyList = new ArrayList(frequency.entrySet());
	Collections.sort(frequencyList, new Comparator() {

		@Override
		public int compare(Object o1, Object o2) {
			// TODO Auto-generated method stub
			Map.Entry e1 = (Map.Entry) o1;
			Map.Entry e2 = (Map.Entry) o2;
			long value1 = (Long) e1.getValue();
			long value2 = (Long) e2.getValue();			
			return (int) (value2 - value1);
		}
		
	});	
    // 4.6. writing sorted frequency into a file 
    frequency_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/frequency_sorted.txt"), "UTF8")));
    for (int i = 0; i < frequencyList.size(); i++) {
    	Map.Entry entry = (Map.Entry) frequencyList.get(i);
    	long freq = (Long) entry.getValue();
    	frequency_out.println(dictionary.get(entry.getKey()) + ": " + freq);
    }    
    // 4.7. close frequency file     
    frequency_out.close();
     
    ///////////////////////////////////////////////////////    
    // 5. creating TFIDF  
    /////////////////////////////////////////////////////// 
    int minDf = Integer.parseInt(args[5]); // [0-9] default:0
    int maxDFPercent = Integer.parseInt(args[6]); // [90, 95, 100] default:100
    float norm = 2; // [0-3] default:2 (must be > 1)    
    TFIDFConverter.processTfIdf(
      new Path(outputDir , DictionaryVectorizer.DOCUMENT_VECTOR_OUTPUT_FOLDER),
      new Path(outputDir), conf, dfData, minDf,
      maxDFPercent, norm, logNormalize, sequentialAccessOutput, namedVectors, reduceTasks);
    
    Path vectorsFolder = new Path(outputDir, "tfidf-vectors");
    Path centroids = new Path(outputDir, "centroids");
    Path clusterOutput = new Path(outputDir, "clusters");
    
    // 5.1. reading tfidf
    List<Vector> tfidf_vectors = Lists.newArrayList();
    for (VectorWritable vectorWritable :
         new SequenceFileDirValueIterable<VectorWritable>(new Path(outputDir + "/tfidf-vectors"), 
        		 PathType.LIST, conf)) {
      Vector tfidf_vector = vectorWritable.get();
	  tfidf_vectors.add(tfidf_vector);
    }
    // 5.2. writing tfidf vector into a file 
    PrintWriter tfidf_out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/tfidf_vector.txt"), "UTF8")));    
    DecimalFormat df = new DecimalFormat("#.##");
    for (Vector vector : tfidf_vectors) {
    	String name = vector.toString();
    	tfidf_out.print(name.substring(0, name.indexOf(':')) + " = {");
    	Iterator<Element> elements = vector.iterateNonZero();
    	while (elements.hasNext()) {
    		Element elem = elements.next();
        	tfidf_out.print(dictionary.get(elem.index()) + ": " + df.format(elem.get()) + ", ");
    	}
    	tfidf_out.println("}");
    }
    // 5.3. close tfidf file    
    tfidf_out.close();
             
    ///////////////////////////////////////////////////////    
    // 6. creating centroids of k clusters  
    ///////////////////////////////////////////////////////     
    int k = Integer.parseInt(args[2]); // [10-40] default:20
    // fixing the seed (avoid randomness for test)
    RandomUtils.useTestSeed();
    RandomSeedGenerator.buildRandom(conf, vectorsFolder, centroids, k, new CosineDistanceMeasure());
    
    // 6.1. creating centroids using canopy clustering
    //double t1 = 0.9;
    //double t2 = 0.85;
    boolean runClustering = false;
    double clusterClassificationThreshold = 0.0;
    boolean runSequential = false;  
    
    //CanopyDriver.run(vectorsFolder, centroids, new CosineDistanceMeasure(), t1, t2, 
    //		runClustering, clusterClassificationThreshold, runSequential);
    
    ///////////////////////////////////////////////////////    
    // 7. running kmeans clustering  
    ///////////////////////////////////////////////////////    
    double convergenceDelta = 0.001; // [0.01, 0.005, 0.001, 0.0001, 0.0] default:0.01
    int maxIterations = 20; // default:20
    runClustering = true;
    KMeansDriver.run(conf, vectorsFolder, new Path(centroids, "part-randomSeed"),
    		//new Path(centroids, "clusters-0-final"), // used for canopy clustering 
      clusterOutput, new CosineDistanceMeasure(), convergenceDelta,
      maxIterations, runClustering, clusterClassificationThreshold, runSequential);
    
    // 7.1. reading clusters to find top terms
    List<Cluster> klusters_list = Lists.newArrayList();
    Hashtable<Integer, Cluster> klusters = new Hashtable<Integer, Cluster>();
    for (ClusterWritable clusterWritable :
         new SequenceFileDirValueIterable<ClusterWritable>(new Path(clusterOutput, "clusters-*-final/part-*"), PathType.GLOB, PathFilters.logsCRCFilter(), conf)) {
      Cluster cluster = clusterWritable.getValue();
      klusters_list.add(cluster);
	  klusters.put(cluster.getId(), cluster);
    }
    
    // 7.2. reading clustering results and writing to a file
    int numTopTerms = 10; // =10
    PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/clusters_"+args[2]+"_"+args[3]+"_"+args[4]+"_"+args[5]+"_"+args[6]+".xml"), "UTF8")));
	writer.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.");
	writer.println("@prefix skos: <http://www.w3.org/2004/02/skos/core#>.");
	writer.println("@prefix sitg: <http://www.sitg.ch/>.");
	writer.println();	
	
    BufferedWriter cluster_out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1]+"/clusters_"+args[2]+"_"+args[3]+"_"+args[4]+"_"+args[5]+"_"+args[6]+".txt"), "UTF8"));
    ClusterDumper clusterDumper = new ClusterDumper(new Path(seqDir), new Path(clusterOutput, "clusteredPoints"));
    Map<Integer, List<WeightedVectorWritable>> clusters = clusterDumper.getClusterIdToPoints();
    cluster_out.write("Number of clusters: " + clusters.size() + "\n");
    String buffer = "";
    int counter = 0;
    for (Integer id : clusters.keySet()) {
    	cluster_out.write("Cluster " + id + " (" + clusters.get(id).size() + ")\n");
    	
    	// 7.3. find top terms of a cluster
    	Cluster kluster = klusters.get(id);
        Hashtable<String, Double> topTerms2 = new Hashtable<String, Double>();
    	if (kluster != null) {
    		Iterator<Element> elements = kluster.getCenter().iterateNonZero();
    		while (elements.hasNext()) {
    			Element elem = elements.next();
    			topTerms2.put(dictionary.get(elem.index()), Double.valueOf(df.format(elem.get())));
    		}
    	}
    	
    	// 7.4. remove shingles in top terms of a cluster
        Hashtable<String, Double> topTerms = new Hashtable<String, Double>();    	
    	for (String topTerm1 : topTerms2.keySet()) {
    		Double value = topTerms2.get(topTerm1);
    		for (String temp : topTerm1.split(" ")) {
    			if (topTerms.containsKey(temp)) {
    				value = Math.max(value, topTerms.get(temp));
    				topTerms.put(temp, value);
    			}
    			else {	
    				topTerms.put(temp, value);
    			}
    		}
    	}
    	// 7.5. sort top terms of a cluster
    	ArrayList topList = new ArrayList(topTerms.entrySet());
        Collections.sort(topList, new TopTermComparator());
        cluster_out.write("TopTerms: " + topList.subList(0, Math.min(topList.size(), numTopTerms)) + "\n");
   
    	writer.println("sitg:collection_" + id + " rdf:type skos:Collection;");
    	
    	List<WeightedVectorWritable> cluster = clusters.get(id); 
    	Iterator<WeightedVectorWritable> documents = cluster.iterator();
    	while (documents.hasNext()) {
    		WeightedVectorWritable doc = documents.next();    		
        	int index = doc.getVector().toString().indexOf(":");
        	String name = doc.getVector().toString().substring(1, index);        	
    		cluster_out.write("  " + String.format("%-40s", name) + ":");
    		counter++;
    		writer.println("    skos:member sitg:concept_" + id + "_" + counter + ";");
 	   		// 7.6. find top terms of a document    		
    		Iterator<Element> elements = doc.getVector().iterateNonZero();
    		Hashtable<String, Double> topTermsDoc2 = new Hashtable<String, Double>();
    		while (elements.hasNext()) {
    			Element elem = elements.next();
    			String word = dictionary.get(elem.index());
    			topTermsDoc2.put(word, Double.valueOf(df.format(elem.get())));
    		}
    		// 7.7 remove shingles in top terms of a document
            Hashtable<String, Double> topTermsDoc = new Hashtable<String, Double>();    	
        	for (String topTermDoc1 : topTermsDoc2.keySet()) {
        		Double value = topTermsDoc2.get(topTermDoc1);
        		for (String temp : topTermDoc1.split(" ")) {
        			if (topTermsDoc.containsKey(temp)) {
        				value = Math.max(value, topTermsDoc.get(temp));
        				topTermsDoc.put(temp, value);
        			}
        			else {	
        				topTermsDoc.put(temp, value);
        			}
        		}
        	}
        	// 7.8. sort top terms of a document
        	ArrayList topListDoc = new ArrayList(topTermsDoc.entrySet());
        	Collections.sort(topListDoc, new TopTermComparator());
        	
        	cluster_out.write(topListDoc.subList(0, Math.min(topListDoc.size(), numTopTerms)).toString());        	
    		cluster_out.write("\n");
    		buffer += "sitg:concept_" + id + "_" + counter + " rdf:type skos:Concept;\n";
    		buffer += "    skos:definition \"" + name.replaceAll(".txt", ".xml") + "\";\n";
    		buffer += "    skos:note \"" + topListDoc.subList(0, Math.min(topListDoc.size(), numTopTerms)) + "\".\n\n";    		
    	}
    	writer.println("    skos:note \"" + topList.subList(0, Math.min(topList.size(), numTopTerms)) + "\".");
    	writer.println();
    }
    
    cluster_out.write("Number of clustered documents: " + counter + "\n");
    cluster_out.close();
    writer.print(buffer);
    writer.close();
    
    ///////////////////////////////////////////////////////    
    // 8. cluster evaluation  
    ///////////////////////////////////////////////////////
    
    // 8.1. inter-cluster distance
    double epsilon = 0.000001;
    DistanceMeasure measure = new CosineDistanceMeasure();
    double max_inter = 0;
    double min_inter = Double.MAX_VALUE;
    double sum_inter = 0;
    int count_inter = 0;
    for (int i = 0; i < klusters_list.size(); i++) {
      for (int j = i + 1; j < klusters_list.size(); j++) {   	  
        double d = measure.distance(klusters_list.get(i).getCenter(), klusters_list.get(j).getCenter());
        min_inter = Math.min(d, min_inter);
        max_inter = Math.max(d, max_inter);
        sum_inter += d;
        count_inter++;        
      }      
    }   
    double avg_inter = (sum_inter/count_inter - min_inter + epsilon) / (max_inter - min_inter + epsilon);
    System.out.println("Maximum Intercluster Distance: " + df.format(max_inter));
    System.out.println("Minimum Intercluster Distance: " + df.format(min_inter));
    System.out.println("Average Intercluster Distance (scaled): " + df.format(avg_inter));
    System.out.println();
    
    // 8.2. intra-cluster distance
    measure = new CosineDistanceMeasure();
    double max_intra = 0;
    double avg_intra = 0;
    int count_intra = 0;    
    Set<Integer> keys = clusters.keySet();
    for (Integer id: keys) {
        double max = 0;
        double min = Double.MAX_VALUE;
        double sum = 0;
        double count = 0;
    	List<WeightedVectorWritable> cluster = clusters.get(id);
    	for (int i = 0; i < cluster.size(); i++) {
    		for (int j = i + 1; j < cluster.size(); j++) {
    			double d = measure.distance(cluster.get(i).getVector(), cluster.get(j).getVector());
    			min = Math.min(d, min);
    			max = Math.max(d, max);
    			sum += d;
    			count++;
    		}
    	}
    	max_intra = Math.max(max, max_intra);
    	double density = (sum/count - min + epsilon) / (max - min + epsilon);
    	if (count == 0) {
    		density = 1;
    		min = 0;
    		max = 0;
    	}
    	avg_intra += density;
    	count_intra++;    	
    	System.out.println("IntraCluster density of cluster " + id + ": " + df.format(density) + " min: " + df.format(min) + " max: " + df.format(max));    	
    	avg_intra = avg_intra / count_intra;
    }
    System.out.println("Average Intracluster Distance (scaled): " + df.format(avg_intra));
    
    // 8.3. Harmonic mean: 2ab/(a+b) => (2a/b)(a+1/b)=2a/(ab+1)
    double harmonic_mean = 2*avg_inter/(avg_inter*avg_intra+1);
    System.out.println("Harmonic mean: " + df.format(harmonic_mean));
    // 8.4. Dunn index: minimal inter-cluster distance to maximal intra-cluster distance
    double dunn_index = min_inter / max_intra;
    System.out.println("Dunn index: " + df.format(dunn_index));
    //*/ 
  }
}
