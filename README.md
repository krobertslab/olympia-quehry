# olympia-quehry

QUEHRY - Query Understanding for Electronic Health Records

## Data

Two clinical question answering datasets are included in this repository.

### ICU dataset

* Present at [`data/quehry/icu_annotations.xml`](data/quehry/icu_annotations.xml).
* Introduced in [this paper](https://aclanthology.org/L16-1598).

```
@inproceedings{roberts2016AnnotatingLogical,
  title={Annotating Logical Forms for EHR Questions},
  author={Roberts, Kirk  and Demner-Fushman, Dina},
  booktitle={Proceedings of the Tenth International Conference on Language Resources and Evaluation (LREC'16)},
  year={2016},
  pages={3772--3778}
}
```

### FHIR dataset

* Present at [`data/quehry/fhir_annotations.xml`](data/quehry/fhir_annotations.xml)
* Introduced in [this paper](https://aclanthology.org/L16-1598).

```
@inproceedings{soni2019using,
  title={Using FHIR to construct a corpus of clinical questions annotated with logical forms and answers},
  author={Soni, Sarvesh and Gudala, Meghana and Wang, Daisy Zhe and Roberts, Kirk},
  booktitle={AMIA Annual Symposium Proceedings},
  pages={1207--1215},
  year={2019},
  organization={American Medical Informatics Association}
}
```

## Dependencies

The project is tested on linux (Ubuntu 20.04).

### MetaMap

Install MetaMap and its Java API using the instructions provided at:
* https://lhncbc.nlm.nih.gov/ii/tools/MetaMap/documentation/Installation.html
* https://lhncbc.nlm.nih.gov/ii/tools/MetaMap/run-locally/JavaApi.html

The project is tested with MetaMap 2018.

Provide the path to MetaMap installation in [`quehry.properties`](quehry.properties).

### UMLS API Key

Create a UMLS account and provide the API key in [`quehry.properties`](quehry.properties).

### LF2FHIR project

Clone and setup the following python repository. It handles mapping the logical forms to their corresponding FHIR queries.

* https://github.com/krobertslab/quehry-lf2fhir

Provide the path to cloned repository in [`quehry.properties`](quehry.properties).

### Jar files

Download the following Java Archive files and place under the [`lib` directory](lib).

* [commons-lang3-3.6.jar](https://archive.apache.org/dist/commons/lang/binaries/commons-lang3-3.6-bin.tar.gz)
* [jackson-annotations-2.10.0.jar](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.10.0/jackson-annotations-2.10.0.jar)
* [jackson-core-2.10.0.jar](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.10.0/jackson-core-2.10.0.jar)
* [jackson-databind-2.10.0.jar](https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.10.0/jackson-databind-2.10.0.jar)
* [jdom2-2.0.3.jar](https://repo1.maven.org/maven2/org/jdom/jdom2/2.0.3/jdom2-2.0.3.jar)
* [json-simple-1.1.1.jar](https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar)
* [liblinear-1.7-with-deps.jar](http://www.java2s.com/Code/JarDownload/liblinear/liblinear-1.7-with-deps.jar.zip)
* [metamap-api-2.0.jar](https://metamap.nlm.nih.gov/maven2/gov/nih/nlm/nls/metamap-api/2.0/metamap-api-2.0.jar)
* [metamaplite-3.5-standalone.jar](https://metamap.nlm.nih.gov/maven2/gov/nih/nlm/nls/metamaplite/3.5/metamaplite-3.5-standalone.jar)
* [prologbeans-4.2.1.jar](https://metamap.nlm.nih.gov/maven2/se/sics/prologbeans/4.2.1/prologbeans-4.2.1.jar)
* [stanford-corenlp-4.2.2.jar](http://nlp.stanford.edu/software/stanford-corenlp-4.2.2.zip)
* [stanford-corenlp-4.2.2-models.jar](http://nlp.stanford.edu/software/stanford-corenlp-4.2.2.zip)

## Compile

```shell
ant
```

## Run

To run the experiments from the paper:

```shell
./bin/quehry.run
```