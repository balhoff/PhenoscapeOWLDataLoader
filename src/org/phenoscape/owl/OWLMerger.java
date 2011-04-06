package org.phenoscape.owl;

import java.io.File;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.util.OWLOntologyMerger;

public class OWLMerger {
    
    /** The data-dir system property should contain the path to a folder with OWL data files to be loaded. */
    public static final String DATA_DIR = "data-dir";
    /** The ontology-dir system property should contain the path to a folder with OWL ontologies to be loaded. */
    public static final String ONTOLOGY_DIR = "ontology-dir";
    /** The merge-file system property should contain the path to a file where the merged OWL ontology should be written. */
    public static final String MERGE_FILE = "merge-file";
    private static final String MERGE_IRI = "http://kb.phenoscape.org/";
    private final OWLOntologyManager manager;
    
    public OWLMerger() throws OWLOntologyCreationException, OWLOntologyStorageException {
        super();
        this.manager = OWLManager.createOWLOntologyManager();
        this.processFolder(new File(System.getProperty(ONTOLOGY_DIR)));
        this.processFolder(new File(System.getProperty(DATA_DIR)));
        final OWLOntologyMerger merger = new OWLOntologyMerger(this.manager);
        final OWLOntology merged = merger.createMergedOntology(this.manager, IRI.create(MERGE_IRI));
        this.manager.saveOntology(merged, new RDFXMLOntologyFormat(), IRI.create(new File(System.getProperty(MERGE_FILE))));
    }

    private void processFolder(File folder) throws OWLOntologyCreationException {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                this.processFolder(file);
            } else if (file.getName().endsWith(".owl")) {
                    this.processFile(file);
            }
        }
    }
    
    private void processFile(File file) throws OWLOntologyCreationException {
        this.manager.loadOntologyFromOntologyDocument(file);
    }
    
    @SuppressWarnings("unused")
    private Logger log() {
        return Logger.getLogger(this.getClass());
    }

    /**
     * @throws OWLOntologyStorageException 
     * @throws OWLOntologyCreationException 
     */
    public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException {
        new OWLMerger();
    }

}
