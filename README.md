NoSPA RDF Data Cube Validator
=============================

### Introduction

This is an RDF Data Cube Validator. Its significant difference from other exsiting validators is that it is not based on SPARQL queries, as its name "NoSPA". Jena library is used to manipulate RDF models. The official SPARQL queries for constraint checks are interpreted and parsed by this validator to search functions with nested statement listing functions provided by Jena and filters for different conditions. It has an outstanding performance because the entire process is executed in the memory. I believe that it is valuable to sacrifice some memory for saving time.

Here is some references and knowledge background for this tool:
  * The official RDF data cube spec: [The RDF Data Cube Vocabulary](http://www.w3.org/TR/vocab-data-cube/)
  * Jena API: [Apache Jena](http://jena.apache.org/index.html)
  * The official SPARQL spec: [SPARQL 1.1 Query Language](http://www.w3.org/TR/sparql11-query/)

### Requirements

JDK (>=5) and Maven if you want to compile by yourself

or 

JVM (>=5) if you want to execute a jar directly

### Installation

This tool is written in Java and managed by Maven so you can compile it easily by yourself. The first thing you need to do is ``git clone`` this repository.

Then you need to do a ``mvn package`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/rdf-data-cube-validator-0.9.jar``. Note that in this case the library for Jena and Log4j is not included in this package.

In the case that you need to run this pacakge independently, you will need to do a ``mvn package assembly:single`` at the root directory of this repository and find the jar file at ``NoSPA-RDF-Data-Cube-Validator/target/rdf-data-cube-validator-0.9-jar-with-dependencies.jar``, which includes all the required libraries to run it.

### Validation

Basically, there are 3 ways to use it:
1.  Use an IDE to modify the code and property file by yourself and run the Main class, without making any packages.

2.  In the case that you need to integrate it into your own project, you have to import the package ``rdf-data-cube-validator-0.9.jar``, create a new validator instance and call the functions to normalize and validate the cube. 

    ``Validator validator = new Validator(inputPath, inputFormat);``
    ``validator.normalizePhase1();``
    ``validator.normalizePhase2();``
    ``validator.checkICAll();``

    The ``inputPath`` is the path of the cube file and ``inputFormat`` indicates the RDF format of the cube file such as RDF/XML, RDF/XML-ABBREV, TURTLE, N-TRIPLES, etc. 

    You may also want to check constraints selectively, in that case you have to explore the public functions of the Validator class by yourself. But please make sure that you have normalized the cube before checking constraints if it is in the abbreviated form. You don't need to normalize it if you are sure that it is in the normalized form.

    Note that the validation result of this tool will be recorded as logs so you need to turn on the logs for this package in the log configuration of your own project. Additionally you have to set a system property ``current.timestamp`` with the value of current time as part of the name of the validation result. Finally, the validation result can be found at ``${user.dir}/validation_result_${current.timestamp}.md``.

3.  In the case that you need to validate the cube file manually and independently, you need to run ``java -jar rdf-data-cube-validator-0.9-jar-with-dependencies.jar ../test.ttl TURTLE``, where the first argument is the file path of the cube to be validated and the second argument is the RDF format of the cube file. If no arguments are provided, the default value will be ``test.ttl`` in the same folder as the jar file and ``TURTLE``. This configuration can be modified by accessing to the ``config.properties`` file.

### Performance

The constraint check IC-12, "No duplicate observations", is the most time-consuming procedure for the entire validation. The motivation of developing this tool is mainly to tackle this issue. 

An initial benchmark shows a dramatic improvement as expected:

Test file: a data cube containing 13970 observations  

Test environment: Ubuntu 14.04 with VMWare, 2 CPU cores of I5-2450M @ 2GHz, 2 GB memory, ordinary HHD

Time consumption for validating IC-12:  

Validation by SPARQL queries with Virtuoso: 1 hour 22 min  
Validation by SPARQL queries with Jena Parser: 58 min  
Validation by NoSPA: 39 sec  
