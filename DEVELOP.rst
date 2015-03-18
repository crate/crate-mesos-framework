=================================
Crate-Mesos-Framework DEVELOPMENT
=================================

Prerequisites
=============

Crate-mesos-framework is written in Java_ 7, so a JDK needs to be installed. On OS X we
recommend using `Oracle's Java`_ and OpenJDK_ on Linux Systems.

Git checkout
============

Clone the repository::

    $ git clone https://github.com/crate/crate-mesos-framework.git

Gradlew - Building Crate-Mesos-Framework
========================================

This project uses Gradle_ as build tool. It can be invoked by executing
``./gradlew``. The first time this command is executed it is bootstrapped
automatically, therefore there is no need to install gradle on the system.

IntelliJ
--------

Gradle can be used to generate project files that can be opened in IntelliJ::

    ./gradlew idea

Building and running Crate-Mesos-Framework
==========================================

To compile run::

    ./gradlew compileJava

In order to run Crate-Mesos-Framework you would need ``mesos`` running. The easiest
way to have it locally is to run it in a virtual box.
You can use ``Vagrant`` development environment for this.

Running Mesos in a Virtualbox
=============================

To build crate-mesos-framework jar run::

    ./gradlew fatJar

Run::

    vagrant up

Then connect to the vagrant box::

    vagrant ssh

Project root directory is available under /vagrant folder inside the virtual box.
To run the crate-mesos framework::

    java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos.jar 127.0.0.1:5050 1

The Mesos WebUI is then available under http://localhost:5050 and Crate is available under http://localhost:4200/admin


.. _Java: http://www.java.com/

.. _`Oracle's Java`: http://www.java.com/en/download/help/mac_install.xml

.. _OpenJDK: http://openjdk.java.net/projects/jdk7/

.. _Gradle: http://www.gradle.org/
