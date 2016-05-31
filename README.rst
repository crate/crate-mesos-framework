=====================
Crate Mesos Framework
=====================

This is an integration framework which allows running and managing the Crate_
database through Mesos_.

.. warning::

    **DISCLAIMER**

    *This is a very early version of Crate-Mesos-Framework;
    document, code behavior, and anything else may change without notice
    and/or break older installations!*


Quick Guide
===========

First, the jar file needs to be built from source::

    ./gradlew fatJar

The framework can be deployed in two ways: (1) executing the jar file from the
command line and (2) launching it via Marathon.

If the framework is deployed using Marathon the jar needs to be copied to the
mesos-slaves. Otherwise it needs to be copied to the mesos-master from where it
is launched using the command line.

In both cases the ``Main`` method requires a ``--crate-version`` argument,
which is the Crate version that should be used. The version number must be
in the format ``x.y.z``.

Current stable Crate version is ``0.54.9``.

Alternatively you can specify a full download URL for Crate. In that case
the URL needs to be in the format: ``http(s)://<HOST><PATH>/crate-<X>.<Y>.<Z><SUFFIX>.tar.gz``


Full Documentation
==================

The documentation for this project has moved into the ``docs/`` folder of this
repository. It is built on ReadTheDocs where it can be found
`here <https://crate.readthedocs.io/projects/crate-mesos-framework/en/latest/>`_.


Are you a Developer?
====================

You can build Crate Mesos Framework on your own with the latest version hosted
on GitHub. To do so, please refer to ``DEVELOP.rst`` for further information.


.. _Crate: https://crate.io
.. _Mesos: http://mesos.apache.org

