package org.phenoscape.owl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.obo.datamodel.Link;
import org.obo.datamodel.OBOClass;
import org.obo.datamodel.impl.OBOSessionImpl;
import org.obo.util.ReasonerUtil;
import org.obo.util.TermUtil;
import org.phenoscape.io.NeXMLReader;
import org.phenoscape.io.nexml_1_0.NeXMLReader_1_0;
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
import org.phenoscape.owl.Vocab.RO;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationSubject;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLQuantifiedObjectRestriction;
import org.semanticweb.owlapi.vocab.DublinCoreVocabulary;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

public class PhenexToOWL {

    final OWLOntologyManager ontologyManager;
    final OWLOntology ontology;
    final OWLDataFactory factory;
    final Map<Character, OWLNamedIndividual> characterToOWLMap = new HashMap<Character, OWLNamedIndividual>();
    final Map<State, OWLNamedIndividual> stateToOWLMap = new HashMap<State, OWLNamedIndividual>();
    final Map<Taxon, OWLNamedIndividual> taxonOTUToOWLMap = new HashMap<Taxon, OWLNamedIndividual>();
    final Map<Phenotype, OWLClass> phenotypeToOWLMap = new HashMap<Phenotype, OWLClass>();
    String uuid;
    int nodeIncrementer = 0;
    
    public static void main(String[] args) throws OWLOntologyStorageException, OWLOntologyCreationException, XmlException, IOException {
    	final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		final OWLOntology ontology = manager.createOntology();
		final File input = new File(args[0]);
		DataSet dataset ;
        try {
            NeXMLReader_1_0 reader = new NeXMLReader_1_0(input, new OBOSessionImpl());
            dataset = reader.getDataSet();
        } catch (XmlException xmle){
            NeXMLReader reader = new NeXMLReader(input, new OBOSessionImpl());
            dataset = reader.getDataSet();
        }
        final PhenexToOWL pto = new PhenexToOWL(ontology);
        pto.translateDataSet(dataset);
		manager.saveOntology(ontology, IRI.create(new File(args[1])));
    }

    public PhenexToOWL(OWLOntology ontology) throws OWLOntologyCreationException {
        this.ontology = ontology;
        this.ontologyManager = ontology.getOWLOntologyManager();
        this.factory = this.ontologyManager.getOWLDataFactory();
        this.ontologyManager.applyChange(new AddImport(this.ontology, this.factory.getOWLImportsDeclaration(IRI.create(RO.IRI))));
    }

    public void translateDataSet(DataSet dataSet) {
        this.clearMaps();
        this.uuid = UUID.randomUUID().toString();
        this.nodeIncrementer = 0;
        final OWLNamedIndividual matrix = this.nextIndividual();
        this.addClass(matrix, this.factory.getOWLClass(IRI.create(CDAO.DATA_MATRIX)));
        if (StringUtils.isNotBlank(dataSet.getPublicationNotes())) {
            final OWLLiteral comment = factory.getOWLLiteral(dataSet.getPublicationNotes());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), matrix.getIRI(), comment);
            this.addAnnotation(IRI.create(PHENOSCAPE.POSITED_BY), matrix.getIRI(), comment);
        }
        //TODO other dataSet metadata annotations
        for (Taxon taxon : dataSet.getTaxa()) {
            final OWLNamedIndividual otu = this.nextIndividual();
            this.addPropertyAssertion(IRI.create(CDAO.HAS_TU), matrix, otu);
            this.translateTaxon(taxon, otu);
        }
        for (Character character : dataSet.getCharacters()) {
            final OWLNamedIndividual owlCharacter = this.nextIndividual();
            this.addPropertyAssertion(IRI.create(CDAO.HAS_CHARACTER), matrix, owlCharacter);
            this.translateCharacter(character, owlCharacter);
        }
        for (Taxon taxon : dataSet.getTaxa()) {
            for (Character character : dataSet.getCharacters()) {
                final State state = dataSet.getStateForTaxon(taxon, character);
                if (state != null) {
                    final OWLNamedIndividual matrixCell = this.nextIndividual();
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
            this.addPropertyAssertion(IRI.create(CDAO.HAS_EXTERNAL_REFERENCE), otu, this.factory.getOWLNamedIndividual(taxonIRI));
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
        final StringBuffer descBuffer = new StringBuffer();
        if (StringUtils.isNotBlank(character.getLabel())) {
            final OWLLiteral label = this.factory.getOWLLiteral(character.getLabel());
            this.addAnnotation(OWLRDFVocabulary.RDFS_LABEL.getIRI(), owlCharacter.getIRI(), label);
            descBuffer.append(character.getLabel() + ":");
        }
        if (StringUtils.isNotBlank(character.getComment())) {
            final OWLLiteral comment = factory.getOWLLiteral(character.getComment());
            this.addAnnotation(OWLRDFVocabulary.RDFS_COMMENT.getIRI(), owlCharacter.getIRI(), comment);
        }
        int stateIndex = 0;
        for (State state : character.getStates()) {
            stateIndex++;
            final OWLNamedIndividual owlState = this.nextIndividual();
            if (StringUtils.isNotBlank(state.getLabel())) {
                descBuffer.append(" " + stateIndex + ". " + state.getLabel());
            }
            this.translateState(state, owlState);
        }
        final String completeDescription = descBuffer.toString(); // for full-text indexing
        if (StringUtils.isNotBlank(completeDescription)) {
        	final OWLLiteral description = factory.getOWLLiteral(completeDescription);
        	this.addAnnotation(DublinCoreVocabulary.DESCRIPTION.getIRI(), owlCharacter.getIRI(), description);
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
        final OWLObjectProperty denotes = this.factory.getOWLObjectProperty(IRI.create(IAO.DENOTES));
        final OWLObjectProperty denotesExemplar = this.factory.getOWLObjectProperty(IRI.create(PHENOSCAPE.DENOTES_EXEMPLAR));
        for (Phenotype phenotype : state.getPhenotypes()) {
            final OWLClass owlPhenotype = this.nextClass();
            final OWLObjectAllValuesFrom denotesOnlyPhenotype = this.factory.getOWLObjectAllValuesFrom(denotes, owlPhenotype);
            final OWLObjectSomeValuesFrom denotesExemplarWithPhenotype = this.factory.getOWLObjectSomeValuesFrom(denotesExemplar, owlPhenotype);
            this.ontologyManager.addAxiom(ontology, this.factory.getOWLClassAssertionAxiom(denotesOnlyPhenotype, owlState));
            this.translatePhenotype(phenotype, owlPhenotype);
            this.instantiateClassAssertion(owlState, denotesExemplarWithPhenotype, true);
        }
    }

    private void translatePhenotype(Phenotype phenotype, OWLClass owlPhenotype) {
        this.phenotypeToOWLMap.put(phenotype, owlPhenotype);
        if (phenotype.getEntity() == null || phenotype.getQuality() == null) {
            return;
        }
        final OWLObjectProperty bearerOf = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.BEARER_OF));
        final OWLObjectProperty partOf = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.PART_OF));
        final OWLClassExpression entity = this.convertOBOClass(phenotype.getEntity());
        final OWLClassExpression qualityTerm = this.convertOBOClass(phenotype.getQuality());
        final OWLClassExpression quality;
        if (phenotype.getRelatedEntity() != null) {
            final OWLClassExpression relatedEntity = this.convertOBOClass(phenotype.getRelatedEntity());
            final OWLObjectProperty towards = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.TOWARDS));
            quality = this.factory.getOWLObjectIntersectionOf(qualityTerm, this.factory.getOWLObjectSomeValuesFrom(towards, relatedEntity));
            for (OWLClass usedClass : relatedEntity.getClassesInSignature()) {
            	this.createRestriction(partOf, usedClass);
            }
        } else {
            quality = qualityTerm;
        }
        final OWLClassExpression eq = this.factory.getOWLObjectIntersectionOf(entity, this.factory.getOWLObjectSomeValuesFrom(bearerOf, quality));
        for (OWLClass usedClass : entity.getClassesInSignature()) {
        	this.createRestriction(partOf, usedClass);
        }
        for (OWLClass usedClass : quality.getClassesInSignature()) {
        	this.createRestriction(bearerOf, usedClass);
        }
        //TODO measurements, counts, etc.
        final OWLObjectProperty hasPart = this.factory.getOWLObjectProperty(IRI.create(OBO_REL.HAS_PART));
        final OWLClassExpression hasPartSomeEQ = this.factory.getOWLObjectSomeValuesFrom(hasPart, eq);
        final OWLObjectProperty hasMember = this.factory.getOWLObjectProperty(IRI.create(PHENOSCAPE.HAS_MEMBER));
        final OWLClassExpression hasMemberSomeHasPart = this.factory.getOWLObjectSomeValuesFrom(hasMember, hasPartSomeEQ);
        this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLEquivalentClassesAxiom(owlPhenotype, hasMemberSomeHasPart));
    }

    private void translateMatrixCell(Taxon taxon, Character character, State state, OWLNamedIndividual matrixCell) {
        this.addClass(matrixCell, this.factory.getOWLClass(IRI.create(CDAO.MATRIX_CELL)));
        this.addPropertyAssertion(IRI.create(CDAO.BELONGS_TO_CHARACTER), matrixCell, this.characterToOWLMap.get(character));
        this.addPropertyAssertion(IRI.create(CDAO.BELONGS_TO_TU), matrixCell, this.taxonOTUToOWLMap.get(taxon));
        this.addPropertyAssertion(IRI.create(CDAO.HAS_STATE), matrixCell, this.stateToOWLMap.get(state));
        if (taxon.getValidName() != null) {
            final IRI taxonIRI = this.convertOBOIRI(taxon.getValidName().getID());
            final OWLNamedIndividual taxonIndividual = this.factory.getOWLNamedIndividual(taxonIRI);
            for (Phenotype phenotype : state.getPhenotypes()) {
                final OWLClass owlPhenotype = this.phenotypeToOWLMap.get(phenotype);
                //final OWLAnnotationProperty positedBy = this.factory.getOWLAnnotationProperty(IRI.create(PHENOSCAPE.POSITED_BY));
                //final OWLAnnotation positedByAnnotation = this.factory.getOWLAnnotation(positedBy, matrixCell.getIRI());
                ///final Set<OWLAnnotation> annotations = Collections.singleton(positedByAnnotation);
                final OWLClassAssertionAxiom classAssertion = this.factory.getOWLClassAssertionAxiom(owlPhenotype, taxonIndividual);
                this.ontologyManager.addAxiom(this.ontology, classAssertion);
                this.instantiateClassAssertion(taxonIndividual, owlPhenotype, true);
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

    private void instantiateClassAssertion(OWLIndividual individual, OWLClassExpression aClass, boolean expandNamedClass) {
        if (aClass instanceof OWLClass) {
            if (expandNamedClass) {
                for (OWLEquivalentClassesAxiom axiom : this.ontology.getEquivalentClassesAxioms(aClass.asOWLClass())) {
                    for (OWLClassExpression expression : axiom.getClassExpressionsMinus(aClass)) {
                        if (expression instanceof OWLObjectSomeValuesFrom) {
                            this.instantiateClassAssertion(individual, expression, false);
                        }
                    }
                }
            } else {
                this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLClassAssertionAxiom(aClass, individual));
            }
        } else if (aClass instanceof OWLQuantifiedObjectRestriction) { // either someValuesFrom or allValuesFrom
            final OWLQuantifiedObjectRestriction restriction = (OWLQuantifiedObjectRestriction)aClass;
            final OWLClassExpression filler = restriction.getFiller();
            final OWLObjectPropertyExpression property = restriction.getProperty();
            // need IRIs for individuals for type materialization
            final OWLIndividual value = this.nextIndividual();
            this.addPropertyAssertion(property, individual, value);
            this.instantiateClassAssertion(value, filler, false);
        } else if (aClass instanceof OWLObjectIntersectionOf) {
            for (OWLClassExpression operand : ((OWLObjectIntersectionOf)aClass).getOperands()) {
                this.instantiateClassAssertion(individual, operand, false);
            }
        } else {
            this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLClassAssertionAxiom(aClass, individual));
        }
    }
    
    private void createRestriction(OWLObjectProperty property, OWLClass ontClass) {
    		//TODO should get this IRI from a more general place
			final String newClassIRI = property.getIRI().toString() + "_some_" + ontClass.getIRI().toString();
			final OWLClass restriction = this.factory.getOWLClass(IRI.create(newClassIRI));
			this.ontologyManager.addAxiom(this.ontology, this.factory.getOWLEquivalentClassesAxiom(restriction, this.factory.getOWLObjectSomeValuesFrom(property, ontClass)));
	}

    private void clearMaps() {
        characterToOWLMap.clear();
        stateToOWLMap.clear();
        taxonOTUToOWLMap.clear();
        phenotypeToOWLMap.clear();
    }
    
    private OWLNamedIndividual nextIndividual() {
    	return this.factory.getOWLNamedIndividual(this.nextIRI());
    }
    
    private OWLClass nextClass() {
    	return this.factory.getOWLClass(this.nextIRI());
    }
    
    private IRI nextIRI() {
    	final String id = "http://kb.phenoscape.org/uuid/" + this.uuid + "-" + this.nodeIncrementer++;
    	return IRI.create(id);
    }
    
    @SuppressWarnings("unused")
	private static Logger log() {
    	return Logger.getLogger(PhenexToOWL.class);
    }

}
