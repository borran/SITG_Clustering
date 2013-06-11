package sitg.parsing

import java.io.File
import java.io.PrintWriter
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream

/**
 * Entity class contains all information about an entity.
 * There are 619 entities in SITG catalog.
 * Each entity has a unique id, filename, title, class, partner, service and resume.
 * It might belong to 0 or more themes.
 * It has several attributes (attribute labels and descriptions).
 *
 * @author Fatemeh Borran
 */
class Entity(val id: Int, val filename: String, val titre: String, val classe: String, 
    val partenaire: String, val service: String, val themes: Seq[String], val resume: String, 
    val attributs_label: Seq[String], val attributs_description: Seq[String]) {
		
	/**
	 * A function to print out the entity.
	 */  
	def print() {
		println("Id: " + id)
		println("Filename: " + filename)
		println("Titre: " + titre)
		println("Classe: " + classe)		
		println("Partenaire: " + partenaire)	
		println("Service: " + service)
		println("Themes: ")
		for (thm <- themes)
			println(thm)
		println("Resume: " + resume)
		println("Attributes: ")
		for (couple <- attributs_label.zip(attributs_description)) {
			println("label: " + couple._1)
			println("description: " + couple._2)		
		}
	}
	
	/**
	 * A function to print entity into a file.
	 * 
	 * @param file to write entity
	 */
	def printToFile(file: File) {
		val writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8")))		
		try { 
			writer.println(titre) 
			writer.println(classe)
			writer.println(partenaire)
			writer.println(service)
			for (thm <- themes)
				writer.println(thm)		  
			writer.println(resume)
			for (couple <- attributs_label.zip(attributs_description))
				writer.println(couple._1 + ": " + couple._2)					
		} 
		finally { writer.close() }
	}
	
	/**
	 * A function to get a field of an entity.
	 * 
	 * @param field : name of the field
	 */	
	def getField(field: String) = field match {
	  	case "titre" 				=> titre
	  	case "classe" 				=> classe
	  	case "partenaire" 			=> partenaire
	  	case "service" 				=> service
	  	case "resume" 				=> resume
	  	case "theme"				=> themes
	  	case "attribut_label" 		=> attributs_label
	  	case "attribut_description" => attributs_description
	  	case others 				=> println("Entitiy.getField("+ field + "): Wrong field name!")
	}
	
	/**
	 * A function to get fields of an entity.
	 * 
	 * @param field : name of the field
	 */	
	def getFields(field: String) = field match {
	  	case "theme" 				=> themes
	  	case "attribut_label" 		=> attributs_label
	  	case "attribut_description" => attributs_description
	  	case others 				=> println("Entity.getFields("+ field + "): Wrong filed name!")
	}	
}