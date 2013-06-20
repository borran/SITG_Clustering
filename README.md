SITG_Clustering
===============

SITG_Clustering is a collection of programs (in Scala and Java) that allows semantic clustering of SITG [Le Système d'Information du Territoire à Genève](http://ge.ch/sitg/) dictionary.
Each entry of the dictionary is an XML document composed of a set of metadata.
The project contains programs for
1) parsing
2) text pre-processing and NLP analyzing
3) regrouping based on vector similarity matrix
4) k-means clustering

The outputs of the similarity matrix and clustering are in SKOS format.


Requirements:
===============
1. Java 1.6
2. Scala 2.9
3. Ant 1.8


Configurations:
===============
1. Set JAVA_HOME
2. Set SCALA_HOME
3. Set custom configurations in config.xml

Input
===============
Put SITG XML Files into a folder called SITG_XML_COMPLET in the root directory.


Useful commands
===============
1. Compile: ant compile

2. Create jar: ant jar

3. Run parser: ant parser
3.1. Set input and output in config.xml (see default values)
3.2. input is a folder containing all SITG XML files
3.3. output is a folder containing different folders of TXT files

4. Run tagger: ant tagger
4.1. Set input and output in config.xml (see default values)
4.2. input is an output of the parser (previous step)
4.3. output is a folder containing tagged TXT files 

5. Run clustering: ant cluster
5.1. Set input, output and parameters in config.xml (see default values)
5.2. input is an output of the tagger (previous step)
5.3. output is a folder containing different results of clustering
5.4. final result of clustering is in turtle format in .xml file in output folder

6. Run similarity: ant similar
6.1. Set input, output and parameters in config.xml (see default values)
6.2. input is an output of the tagger (step 4)
6.3. output is a folder containing different results of similarity
6.4. final result of similarity is in turtle format in .xml file in output folder

7. Clean project (remove build folder): ant clean

8. Cleanup project (remove all folders created by the project): ant cleanup


