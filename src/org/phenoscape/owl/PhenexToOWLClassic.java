package org.phenoscape.owl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.obo.datamodel.Link;
import org.obo.datamodel.OBOClass;
import org.obo.util.ReasonerUtil;
import org.obo.util.TermUtil;
import org.phenoscape.model.Character;
import org.phenoscape.model.DataSet;
import org.phenoscape.model.Phenotype;
import org.phenoscape.model.Specimen;
import org.phenoscape.model.State;
import org.phenoscape.model.Taxon;
import org.phenoscape.owl.Vocab.CDAO;
import org.phenoscape.owl.Vocab.DWC;
import org.phenoscape.owl.Vocab.IAO;
import org.phenoscape.owl.Vocab.OBO_REL;
import org.phenoscape.owl.Vocab.PHENOSCAPE;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

public class PhenexToOWLClassic {

    final OWLOntologyManager ontologyManager;
    final OWLOntology ontology;
    final OWLDataFactory factory;
    final Map<Character, OWLNamedIndividual> characterToOWLMap = new HashMap<Character, OWLNamedIndividual>();
    final Map<State, OWLNamedIndividual> stateToOWLMap = new HashMap<State, OWLNamedIndividual>();
    final Map<Taxon, OWLNamedIndividual> taxonOTUToOWLMap = new HashMap<Taxon, OWLNamedIndividual>();
    final Map<Phenotype, OWLClass> phenotypeToOWLMap = new HashMap<Phenotype, OWLClass>(); 

    public PhenexToOWLClassic(OWLOntology ontology) throws OWLOntologyCreationException {
        this.ontology = ontology;
        this.ontologyManager = ontology.getOWLOntologyManager();
        this.factory = this.ontologyManager.getOWLDataFactory();
    }

    public void translateDataSet(DataSet dataSet) {
        this.clearMaps();
        final String publicationID = dataSet.getPublication().split(":")[1];
        final String publicationURI = PHENOSCAPE.PUBLICATION + "/" + publicationID;
        final IRI matrixIRI = IRI.create(publicationURI + "/matrix");
        final OWLNamedIndividual matrix = this.factory.getOWLNamedIndividual(matrixIRI);
        this.addClass(matrix, this.factory.getOWLClass(IRI.create(CDAO.DATA_MATRIX)));
        if (StringUtils.isNotBlank(dataSet.getPublicationNotes())) {
            final OWLLiteral comment = factory.getOWLLiteral(dataSet.getPublicationNotes());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), matrixIRI, comment);
            this.addAnnotation(IRI.create(PHENOSCAPE.POSITED_BY), matrixIRI, comment);
        }
        //TODO other dataSet metadata annotations
        int taxonIndex = 0;
        for (Taxon taxon : dataSet.getTaxa()) {
            taxonIndex++;
            final IRI otuIRI = IRI.create(matrix.getIRI().toURI().toString() + "/otu/" + taxonIndex);
            final OWLNamedIndividual otu = this.factory.getOWLNamedIndividual(otuIRI);
            this.addPropertyAssertion(IRI.create(CDAO.HAS_TU), matrix, otu);
            this.translateTaxon(taxon, otu);
        }
        int characterIndex = 0;
        for (Character character : dataSet.getCharacters()) {
            characterIndex++;
            final IRI characterIRI = IRI.create(matrix.getIRI().toURI().toString() + "/character/" + characterIndex);
            final OWLNamedIndividual owlCharacter = this.factory.getOWLNamedIndividual(characterIRI);
            this.addPropertyAssertion(IRI.create(CDAO.HAS_CHARACTER), matrix, owlCharacter);
            this.translateCharacter(character, owlCharacter);
        }
        for (Taxon taxon : dataSet.getTaxa()) {
            for (Character character : dataSet.getCharacters()) {
                final State state = dataSet.getStateForTaxon(taxon, character);
                if (state != null) {
                    final IRI matrixCellIRI = IRI.create(this.characterToOWLMap.get(character).getIRI().toURI().toString() + "/otu/" + dataSet.getTaxa().indexOf(taxon));
                    final OWLNamedIndividual matrixCell = this.factory.getOWLNamedIndividual(matrixCellIRI);
                    this.translateMatrixCell(taxon, character, state, matrixCell);
                }
            }
        }
    }

    private void translateTaxon(Taxon taxon, OWLNamedIndividual otu) {
        this.taxonOTUToOWLMap.put(taxon, otu);
        this.addClass(otu, this.factory.getOWLClass(IRI.create(CDAO.OTU)));
        if (StringUtils.isNotBlank(taxon.getPublicationName())) {
            final OWLLiteral label = this.factory.getOWLLiteral(taxon.getPublicationName());
            this.addAnnotation(OWLRDFVocabulary.RDFS_LABEL.getIRI(), otu.getIRI(), label);
        }
        if (taxon.getValidName() != null) {
            final IRI taxonIRI = this.convertOBOIRI(taxon.getValidName().getID());
            this.addClass(this.factory.getOWLNamedIndividual(taxonIRI), this.factory.getOWLClass(IRI.create(PHENOSCAPE.TAXON)));
            this.addPropertyAssertion(IRI.create(PHENOSCAPE.REPRESENTS_TAXON), otu, this.factory.getOWLNamedIndividual(taxonIRI));
        }
        if (StringUtils.isNotBlank(taxon.getComment())) {
            final OWLLiteral comment = factory.getOWLLiteral(taxon.getComment());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), otu.getIRI(), comment);
        }
        for (Specimen specimen : taxon.getSpecimens()) {
            final OWLAnonymousIndividual owlSpecimen = this.factory.getOWLAnonymousIndividual();
            this.addPropertyAssertion(IRI.create(DWC.HAS_SPECIMEN), otu, owlSpecimen);
            this.translateSpecimen(specimen, owlSpecimen);
        }
    }

    private void translateSpecimen(Specimen specimen, OWLAnonymousIndividual owlSpecimen) {
        this.addClass(owlSpecimen, this.factory.getOWLClass(IRI.create(PHENOSCAPE.SPECIMEN)));
        if (specimen.getCollectionCode() != null) {
            final OWLIndividual collection = this.factory.getOWLNamedIndividual(this.convertOBOIRI(specimen.getCollectionCode().getID()));
            this.addPropertyAssertion(IRI.create(DWC.SPECIMEN_TO_COLLECTION), owlSpecimen, collection);
        }
        if (StringUtils.isNotBlank(specimen.getCatalogID())) {
            final OWLDataProperty property = this.factory.getOWLDataProperty(IRI.create(DWC.SPECIMEN_TO_CATALOG_ID));
            this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLDataPropertyAssertionAxiom(property, owlSpecimen, specimen.getCatalogID()));
        }
    }

    private void translateCharacter(Character character, OWLNamedIndividual owlCharacter) {
        this.characterToOWLMap.put(character, owlCharacter);
        this.addClass(owlCharacter, this.factory.getOWLClass(IRI.create(CDAO.STANDARD_CHARACTER)));
        if (StringUtils.isNotBlank(character.getLabel())) {
            final OWLLiteral label = this.factory.getOWLLiteral(character.getLabel());
            this.addAnnotation(OWLRDFVocabulary.RDFS_LABEL.getIRI(), owlCharacter.getIRI(), label);
        }
        if (StringUtils.isNotBlank(character.getComment())) {
            final OWLLiteral comment = factory.getOWLLiteral(character.getComment());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), owlCharacter.getIRI(), comment);
        }
        int stateIndex = 0;
        for (State state : character.getStates()) {
            stateIndex++;
            final IRI stateIRI = IRI.create(owlCharacter.getIRI().toURI().toString() + "/state/" + stateIndex);
            final OWLNamedIndividual owlState = this.factory.getOWLNamedIndividual(stateIRI);
            this.translateState(state, owlState);
        }
    }

    private void translateState(State state, OWLNamedIndividual owlState) {
        this.stateToOWLMap.put(state, owlState);
        this.addClass(owlState, this.factory.getOWLClass(IRI.create(CDAO.STANDARD_STATE)));
        if (StringUtils.isNotBlank(state.getLabel())) {
            final OWLLiteral label = this.factory.getOWLLiteral(state.getLabel());
            this.addAnnotation(OWLRDFVocabulary.RDFS_LABEL.getIRI(), owlState.getIRI(), label);
        }
        if (StringUtils.isNotBlank(state.getComment())) {
            final OWLLiteral comment = factory.getOWLLiteral(state.getComment());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), owlState.getIRI(), comment);
        }
        int phenotypeIndex = 0;
        final OWLObjectProperty denotes = this.factory.getOWLObjectProperty(IRI.create(IAO.DENOTES));
        for (Phenotype phenotype : state.getPhenotypes()) {
            phenotypeIndex++;
            final IRI phenotypeIRI = IRI.create(owlState.getIRI().toURI().toString() + "/phenotype/" + phenotypeIndex);
            final OWLClass owlPhenotype = this.factory.getOWLClass(phenotypeIRI);
            final OWLObjectAllValuesFrom denotesOnlyPhenotype = this.factory.getOWLObjectAllValuesFrom(denotes, owlPhenotype);
            this.ontologyManager.addAxiom(ontology, this.factory.getOWLClassAssertionAxiom(denotesOnlyPhenotype, owlState));
            this.translatePhenotype(phenotype, owlPhenotype);
        }
    }

    private void translatePhenotype(Phenotype phenotype, OWLClass owlPhenotype) {
        this.phenotypeToOWLMap.put(phenotype, owlPhenotype);
        if (phenotype.getEntity() == null || phenotype.getQuality() == null) {
            return;
        }
        final OWLObjectProperty inheresIn = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.INHERES_IN));
        final OWLClassExpression entity = this.convertOBOClass(phenotype.getEntity());
        final OWLClassExpression qualityTerm = this.convertOBOClass(phenotype.getQuality());
        final OWLClassExpression quality;
        if (phenotype.getRelatedEntity() != null) {
            final OWLClassExpression relatedEntity = this.convertOBOClass(phenotype.getRelatedEntity());
            final OWLObjectProperty towards = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.TOWARDS));
            quality = this.factory.getOWLObjectIntersectionOf(qualityTerm, this.factory.getOWLObjectSomeValuesFrom(towards, relatedEntity));
        } else {
            quality = qualityTerm;
        }
        final OWLClassExpression eq = this.factory.getOWLObjectIntersectionOf(quality, this.factory.getOWLObjectSomeValuesFrom(inheresIn, entity));
        //TODO measurements, counts, etc.
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLEquivalentClassesAxiom(owlPhenotype, eq));
    }

    private void translateMatrixCell(Taxon taxon, Character character, State state, OWLNamedIndividual matrixCell) {
        this.addClass(matrixCell, this.factory.getOWLClass(IRI.create(CDAO.MATRIX_CELL)));
        this.addPropertyAssertion(IRI.create(CDAO.BELONGS_TO_CHARACTER), matrixCell, this.characterToOWLMap.get(character));
        this.addPropertyAssertion(IRI.create(CDAO.BELONGS_TO_TU), matrixCell, this.taxonOTUToOWLMap.get(taxon));
        this.addPropertyAssertion(IRI.create(CDAO.HAS_STATE), matrixCell, this.stateToOWLMap.get(state));
        if (taxon.getValidName() != null) {
            final IRI taxonIRI = this.convertOBOIRI(taxon.getValidName().getID());
            final OWLClass taxonClass = this.factory.getOWLClass(taxonIRI);
            for (Phenotype phenotype : state.getPhenotypes()) {
                final OWLClass eq = this.phenotypeToOWLMap.get(phenotype);
                final OWLObjectProperty exhibits = this.factory.getOWLObjectProperty(IRI.create(PHENOSCAPE.EXHIBITS));
                final OWLClassExpression exhibitsSomeEQ = this.factory.getOWLObjectSomeValuesFrom(exhibits, eq);
                final OWLAnnotationProperty positedBy = this.factory.getOWLAnnotationProperty(IRI.create(PHENOSCAPE.POSITED_BY));
                final OWLAnnotation positedByAnnotation = this.factory.getOWLAnnotation(positedBy, matrixCell.getIRI());
                final Set<OWLAnnotation> annotations = Collections.singleton(positedByAnnotation);
                final OWLSubClassOfAxiom annotatedSubClassOfAxiom = this.factory.getOWLSubClassOfAxiom(taxonClass, exhibitsSomeEQ, annotations);
                this.ontologyManager.addAxiom(this.ontology, annotatedSubClassOfAxiom);
            }
        }
    }

    private OWLClassExpression convertOBOClass(OBOClass term) {
        if (TermUtil.isIntersection(term)) {
            final Set<OWLClassExpression> operands = new HashSet<OWLClassExpression>();
            final OBOClass genus = ReasonerUtil.getGenus(term);
            operands.add(this.convertOBOClass(genus));
            for (Link differentia : ReasonerUtil.getDifferentia(term)) {
                final OWLObjectProperty property = this.factory.getOWLObjectProperty(this.convertOBOIRI(differentia.getType().getID()));
                final OWLClassExpression filler = this.convertOBOClass((OBOClass)(differentia.getParent()));
                operands.add(this.factory.getOWLObjectSomeValuesFrom(property, filler));
            }
            return this.factory.getOWLObjectIntersectionOf(operands);
        } else {
            return this.factory.getOWLClass(this.convertOBOIRI(term.getID()));
        }
    }

    private IRI convertOBOIRI(String oboID) {
        return IRI.create("http://purl.obolibrary.org/obo/" + oboID.replaceAll(":", "_"));
    }

    private void addAnnotation(IRI property, OWLAnnotationSubject subject, OWLAnnotationValue value) {
        final OWLAnnotationProperty annotationProperty = this.factory.getOWLAnnotationProperty(property);
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLDeclarationAxiom(annotationProperty));
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLAnnotationAssertionAxiom(annotationProperty, subject, value));
    }

    private void addPropertyAssertion(IRI propertyIRI, OWLIndividual subject, OWLIndividual object) {
        final OWLObjectProperty property = this.factory.getOWLObjectProperty(propertyIRI);
        this.addPropertyAssertion(property, subject, object);
    }

    private void addPropertyAssertion(OWLObjectPropertyExpression property, OWLIndividual subject, OWLIndividual object) {
        if (!property.isAnonymous()) {
            this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLDeclarationAxiom(property.asOWLObjectProperty()));
        }
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLObjectPropertyAssertionAxiom(property, subject, object));
    }

    private void addClass(OWLIndividual individual, OWLClassExpression aClass) {
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLClassAssertionAxiom(aClass, individual));
    }

    private void clearMaps() {
        characterToOWLMap.clear();
        stateToOWLMap.clear();
        taxonOTUToOWLMap.clear();
        phenotypeToOWLMap.clear();
    }

}
