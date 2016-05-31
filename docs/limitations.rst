===========
Limitations
===========

The Crate Mesos Framework still has various limitations and known issues.

* There is no automatic handling of cluster failures.
* The overall cluster health needs to be monitored separately,
  using the Crate Admin UI (running on port ``4200`` at path ``/admin``)
  or other third party tools.
* The cluster does not automatically resize depending on used resources.
* Although Crate requires a minimum disk size to start, the disk usage
  is not monitored inside the framework further more. This can be done using
  the Admin UI or plain SQL.
* A zero-downtime upgrade is not possible at the moment.

