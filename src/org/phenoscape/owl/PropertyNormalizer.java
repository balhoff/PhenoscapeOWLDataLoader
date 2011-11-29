package org.phenoscape.owl;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.util.OWLEntityRenamer;

public class PropertyNormalizer {

    private static final Map<IRI, IRI> properties = new HashMap<IRI, IRI>();
    static {
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_part_of"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/TODO_part_of"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL#_part_of"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000050"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_has_part"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/TODO_has_part"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000051"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/TODO_develops_from"), IRI.create("http://purl.obolibrary.org/obo/RO_0002202"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/tao#develops_from"), IRI.create("http://purl.obolibrary.org/obo/RO_0002202"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_bearer_of"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000053"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL#_has_quality"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000053"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/tao#has_quality"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000053"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/pato#_inheres_in"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000052"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_inheres_in"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000052"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/TODO_inheres_in"), IRI.create("http://purl.obolibrary.org/obo/BFO_0000052"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/RO_overlaps"), IRI.create("http://purl.obolibrary.org/obo/RO_0002131"));
        properties.put(IRI.create("http://purl.obolibrary.org/obo/towards"), IRI.create("http://purl.obolibrary.org/obo/OBO_REL_towards")); //TODO check proper URI
        properties.put(IRI.create("http://purl.obolibrary.org/obo/TODO_towards"), IRI.create("http://purl.obolibrary.org/obo/OBO_REL_towards")); //TODO check proper URI
    }

    public void normalize(File file) throws OWLOntologyCreationException, OWLOntologyStorageException {
        final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        final OWLOntology ontology = manager.loadOntologyFromOntologyDocument(file);
        this.normalize(ontology);
        manager.saveOntology(ontology);
    }

    public void normalize(OWLOntology ontology) {
        final OWLEntityRenamer renamer = new OWLEntityRenamer(ontology.getOWLOntologyManager(), Collections.singleton(ontology));
        for (Entry<IRI, IRI> entry : properties.entrySet()) {
            final List<OWLOntologyChange> changes = renamer.changeIRI(entry.getKey(), entry.getValue());
            ontology.getOWLOntologyManager().applyChanges(changes);
        }
    }

    /**
     * @param args any number of file paths
     */
    public static void main(String[] args) {
        final PropertyNormalizer normalizer = new PropertyNormalizer();
        for (String filename : args) {
            try {
                normalizer.normalize(new File(filename));
            } catch (OWLOntologyCreationException e) {
                log().error("Failed to load ontology: " + filename, e);
            } catch (OWLOntologyStorageException e) {
                log().error("Failed to save ontology: " + filename, e);
            }
        }
    }

    private static Logger log() {
        return Logger.getLogger(PropertyNormalizer.class);
    }

}
