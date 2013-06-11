package sitg.parsing

import scala.xml.XML
import scala.collection.mutable.ListBuffer
import java.io.File
import java.io.PrintWriter
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import scala.Array.canBuildFrom

/**
 * This file contains an XML parser for the SITG Catalog.
 * 
 * @author Fatemeh Borran
 */
object XMLParser {
  
	def main(args:Array[String]) = {
	 	
		// arguments: input output
		if (args.length != 2)
			System.out.println("Usage: java sitg.parsing.XMLParser input output");
				 	
		val start = System.currentTimeMillis()
		
		println("Parsing XML files...")		
		
		// SITG catalog contains XML files (INPUT)
		val sitg_catalog = new File(args(0))  
		
		// SITG texts contains TXT files of SITG catalog (OUTPUT) 
		new File(args(1)).mkdir()
		new File(args(1)+"/ALL").mkdir()
		new File(args(1)+"/TITRE").mkdir()
		new File(args(1)+"/TITRE/UNIQUE").mkdir()
		new File(args(1)+"/CLASSE").mkdir()
		new File(args(1)+"/CLASSES").mkdir()		
		new File(args(1)+"/PARTENAIRE").mkdir()
		new File(args(1)+"/PARTENAIRE/UNIQUE").mkdir()
		new File(args(1)+"/SERVICE").mkdir()
		new File(args(1)+"/SERVICE/UNIQUE").mkdir()
		new File(args(1)+"/THEMES").mkdir()
		new File(args(1)+"/THEMES/UNIQUE").mkdir()
		new File(args(1)+"/RESUME").mkdir()
		new File(args(1)+"/ATTRS_LABEL").mkdir()
		new File(args(1)+"/ATTRS_LABEL/UNIQUE").mkdir()
		new File(args(1)+"/ATTRS_DESCR").mkdir()
		
		new File(args(1)+"/TITRE_RESUME").mkdir()
				
		// extracting all XML files
		var xmlFiles = recursiveListFiles(sitg_catalog).filter(_.getName.endsWith(".xml"))
		println("Number of XML files: " + xmlFiles.length)
        
		// id of an entity
		var id = 0				
		
		// list of entities
		var entities = new ListBuffer[Entity]()
		
		for (file <- xmlFiles) {
			// load XML file
			val xmlFile = XML.loadFile(file.getPath);
			id = id + 1
			val filename = file.getName
			// using XPath notation to find all Title Nodes in the XML file and extract their text 
			// there is only one resTitle Node in SITG XML files
			val titre = (xmlFile\\"dataIdInfo"\\"resTitle").text
			//if (titre.equals(""))
			//	println("empty titre: " + filename)				
			// using XPath notation to find all Classe Nodes in the XML file and extract their text 
			// there is only one Title Node in SITG XML files
			val classe = (xmlFile\\"title").text
			//if (classe.equals(""))
			//	println("empty classe: " + filename)				
			// using XPath notation to find all Partenaire Nodes in the XML file and extract their text
			// there are several rpXTPartName Node in SITG XML files
			val partenaire = (xmlFile\\"dataIdInfo"\\"rpXTPartName").text
			//if (partenaire.equals(""))
			//	println("empty partenaire: " + filename)
			// using XPath notation to find all Service Nodes in the XML file and extract their text
			// there are several rpOrgName Node in SITG XML files
			val service = (xmlFile\\"dataIdInfo"\\"rpOrgName").text	
			//if (service.equals(""))
			//	println("empty service: " + filename)			
			// using XPath notation to find all Abstract Nodes in the XML file and extract their text
			// there is only one idAbs Node in SITG XML files
			val resume = (xmlFile\\"dataIdInfo"\\"idAbs").text
			//if (resume.split(" ").size < 5)
			//	println(filename + ": " + resume.split(" ").size)
			//if (resume.equals(""))
			//	println("empty resume: " + filename)				
			// using XPath notation to find all Theme Nodes in the XML file and extract their text
			// there is only one descXT Node in SITG XML files
			val themes = (xmlFile\\"dataIdInfo"\\"tpCat"\\"descXT") map (node => node.text)
			//if (themes.length == 0)
			//	println("empty themes: " + filename)
			// using XPath notation to find all Attribute Nodes in the XML file and extract their text
			// there are several atXTdesc Node in SITG XML files
			val attributs = (xmlFile\\"eainfo"\\"attr")
			//if (attributs.length == 0)
			//	println("empty attributs: " + filename)			
			val attributs_label = attributs map (node => (node\\"attrlabl").text.toUpperCase)
			val attributs_description = attributs map (node => (node\\"atXTdesc").text)
			
			//if (titre.equals(classe))
			//	println(filename + "; " + partenaire + "; " + service)
				
			// creating the entity
			val entity = new Entity(id, filename, titre, classe, partenaire, service, themes, 
			    resume, attributs_label, attributs_description)			
						
			// writing the entity into a text file
			val output = filename.substring(0, file.getName.length-3)+"txt"
			entity.printToFile(new File(args(1)+"/ALL/"+output))
			
			// writing the fields of the entity into separate files
			printToFile(new File(args(1)+"/TITRE/"+output), titre)
			printToFile(new File(args(1)+"/CLASSE/"+output), classe)			
			val classes = classe.replace("A.", "").replace('_', ' ')
			printToFile(new File(args(1)+"/CLASSES/"+output), classes)			
			printToFile(new File(args(1)+"/PARTENAIRE/"+output), partenaire)
			printToFile(new File(args(1)+"/SERVICE/"+output), service)
			printToFile(new File(args(1)+"/THEMES/"+output), themes)
			printToFile(new File(args(1)+"/RESUME/"+output), resume)	
			printToFile(new File(args(1)+"/ATTRS_LABEL/"+output), attributs_label)
			printToFile(new File(args(1)+"/ATTRS_DESCR/"+output), attributs_description)							
			
			// writing the titre + resume into a text file
			printToFile(new File(args(1)+"/TITRE_RESUME/"+output), titre + "\n" + resume)			
				
			// adding the entity into a buffer for statistics
			entities += entity			
		}
		
		// writing the fields of all entities into the same file
		val titres = for {t <- entities} yield t.titre
		printToFile(new File(args(1)+"/TITRE/ALL.TXT"), titres)
		val unique_titres = for {t <- entities.groupBy(_.titre)} yield t._1
		printToFile(new File(args(1)+"/TITRE/ALL_UNIQUE.TXT"), unique_titres)		
		for (t <- unique_titres; if (!t.equals("")))
			printToFile(new File(args(1)+"/TITRE/UNIQUE/"+t.replace("/", "")+".txt"), t)
		
		val classes = for {t <- entities} yield t.classe
		printToFile(new File(args(1)+"/CLASSE/ALL.TXT"), classes)
		val unique_classes = for {t <- entities.groupBy(_.classe)} yield t._1
		printToFile(new File(args(1)+"/CLASSE/ALL_UNIQUE.TXT"), unique_classes)		
		
		val partenaires = for {t <- entities} yield t.partenaire
		printToFile(new File(args(1)+"/PARTENAIRE/ALL.TXT"), partenaires)		
		val unique_partenaires = for {t <- entities.groupBy(_.partenaire)} yield t._1
		printToFile(new File(args(1)+"/PARTENAIRE/ALL_UNIQUE.TXT"), unique_partenaires)
		for (t <- unique_partenaires; if (!t.equals("")))
			printToFile(new File(args(1)+"/PARTENAIRE/UNIQUE/"+t+".txt"), t)		
		
		val services = for {t <- entities} yield t.service
		printToFile(new File(args(1)+"/SERVICE/ALL.TXT"), services)				
		val unique_services = for {t <- entities.groupBy(_.service)} yield t._1
		printToFile(new File(args(1)+"/SERVICE/ALL_UNIQUE.TXT"), unique_services)
		for (t <- unique_services; if (!t.equals("")))
			printToFile(new File(args(1)+"/SERVICE/UNIQUE/"+t+".txt"), t)		
		
		val themes = for {t <- entities; t1 <- t.themes} yield t1
		printToFile(new File(args(1)+"/THEMES/ALL.TXT"), themes)		
		val unique_themes = new ListBuffer[String](); 
		for {t <- entities.groupBy(_.themes); t1 <- t._1; if (!unique_themes.contains(t1))}
			unique_themes += t1		
		printToFile(new File(args(1)+"/THEMES/ALL_UNIQUE.TXT"), unique_themes)
		for (t <- unique_themes)
			printToFile(new File(args(1)+"/THEMES/UNIQUE/"+t+".txt"), t)			

		val resumes = for {t <- entities} yield t.resume
		printToFile(new File(args(1)+"/RESUME/ALL.TXT"), resumes)			
		val unique_resumes = for {t <- entities.groupBy(_.resume)} yield t._1
		printToFile(new File(args(1)+"/RESUME/ALL_UNIQUE.TXT"), unique_resumes)
		
		val attrs_label = for {t <- entities; t1 <- t.attributs_label} yield t1
		printToFile(new File(args(1)+"/ATTRS_LABEL/ALL.TXT"), attrs_label)				
		val unique_attrs_label = new ListBuffer[String]();
		for {t <- entities.groupBy(_.attributs_label); t1 <- t._1; if (!unique_attrs_label.contains(t1))}
			unique_attrs_label += t1		
		printToFile(new File(args(1)+"/ATTRS_LABEL/ALL_UNIQUE.TXT"), unique_attrs_label)
		for (t <- unique_attrs_label)
			printToFile(new File(args(1)+"/ATTRS_LABEL/UNIQUE/"+t+".txt"), t)
			
		val attrs_descr = for {t <- entities; t1 <- t.attributs_description} yield t1
		printToFile(new File(args(1)+"/ATTRS_DESCR/ALL.TXT"), attrs_descr)
		val unique_attrs_descr = new ListBuffer[String]();
		for {t <- entities.groupBy(_.attributs_description); t1 <- t._1; if (!unique_attrs_descr.contains(t1))}
			unique_attrs_descr += t1		
		printToFile(new File(args(1)+"/ATTRS_DESCR/ALL_UNIQUE.TXT"), unique_attrs_descr)		
		
		println("All entities are extracted.")
		
		val end = System.currentTimeMillis()
		
		println("Execution time: " + (end-start)/1000 + " seconds")
	}

	/**
	 * A recursive function to list all files and subdirectories of a directory.
	 * 
	 * @param file : directory to be listed
	 */
	def recursiveListFiles(file: File): Array[File] = {
		val allfiles = file.listFiles
		allfiles ++ allfiles.filter(_.isDirectory).flatMap(recursiveListFiles)
	}		
	
	/**
	 * A function to print text into a file.
	 * 
	 * @param file to write entity
	 * @param text to write
	 */
	def printToFile(file: File, text: String) {
		val writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8")))		
		try { 
			writer.println(text)
		} 
		finally { writer.close() }
	}	
	
	/**
	 * A function to print text into a file.
	 * 
	 * @param file to write entity
	 * @param text to write
	 */
	def printToFile(file: File, texts: Iterable[String]) {
		val writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8")))		
		try { 
			for (text <- texts)
				writer.println(text)
		} 
		finally { writer.close() }
	}		
		
}