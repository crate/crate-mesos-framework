Crate-Mesos-Framework
=====================

This is an integration framework allows running and managing Crate_ database through Mesos_.

Are you a Developer?
====================

You can build Crate-Mesos-Framework on your own with the latest version hosted on GitHub.
To do so, please refer to ``DEVELOP.rst`` for further information.


Build Crate-mesos jar::

    ./gradlew fatJar


Run Mesos in Virtualbox
=======================

In order to get mesos up and running ``vagrant`` can be used. Simply run::

    vagrant up

Then connect to the vagrant box::

    vagrant ssh

and run the crate-mesos framework::

    java -Djava.library.path=/usr/local/lib -jar /vagrant/build/libs/crate-mesos.jar 127.0.0.1:5050 1


The Mesos WebUI is then available under http://localhost:5050 and Crate is available under http://localhost:4200/admin


.. _Crate: https://github.com/crate/crate

.. _Mesos: http://mesos.apache.org