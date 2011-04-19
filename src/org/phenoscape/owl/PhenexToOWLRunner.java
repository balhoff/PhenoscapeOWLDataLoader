package org.phenoscape.owl;

import java.io.File;
import java.io.IOException;

import org.apache.xmlbeans.XmlException;
import org.obo.datamodel.impl.OBOSessionImpl;
import org.phenoscape.io.NeXMLReader;
import org.phenoscape.io.nexml_1_0.NeXMLReader_1_0;
import org.phenoscape.model.DataSet;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

public class PhenexToOWLRunner {
    
    /** The data-dir system property should contain the path to a folder with NeXML data files to be loaded. */
    public static final String DATA_DIR = "data-dir";
    private final OWLOntologyManager manager;

    public PhenexToOWLRunner() throws OWLOntologyCreationException, XmlException, IOException, OWLOntologyStorageException {
        this.manager = OWLManager.createOWLOntologyManager();
        this.processDataFolder(new File(System.getProperty(DATA_DIR)));
    }
    
    private void processDataFolder(File folder) throws OWLOntologyCreationException, XmlException, IOException, OWLOntologyStorageException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                this.processDataFolder(file);
            } else if (file.getName().endsWith(".xml")) {
                this.processDataFile(file);            
            }
        }
    }

    private void processDataFile(File file) throws OWLOntologyCreationException, XmlException, IOException, OWLOntologyStorageException {
        final OWLOntology ontology = this.manager.createOntology(IRI.create(file));
        DataSet dataset ;
        try {
            NeXMLReader_1_0 reader = new NeXMLReader_1_0(file, new OBOSessionImpl());
            dataset = reader.getDataSet();
        } catch (XmlException xmle){
            NeXMLReader reader = new NeXMLReader(file, new OBOSessionImpl());
            dataset = reader.getDataSet();
        }
        final PhenexToOWL translator = new PhenexToOWL(ontology);
        translator.translateDataSet(dataset);
        this.manager.saveOntology(ontology, new RDFXMLOntologyFormat(), IRI.create(this.getOWLFile(file)));
        this.manager.removeOntology(ontology);
    }
    
    private File getOWLFile(File nexmlFile) {
        final String path = nexmlFile.getPath();
        final String newPath = path.substring(0, (path.length() - 3)) + "owl";
        return new File(newPath);
    }

    /**
     * @throws OWLOntologyCreationException 
     * @throws IOException 
     * @throws XmlException 
     * @throws OWLOntologyStorageException 
     */
    public static void main(String[] args) throws OWLOntologyCreationException, XmlException, IOException, OWLOntologyStorageException {
        new PhenexToOWLRunner();
    }

}
