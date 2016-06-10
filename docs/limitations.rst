===========
Limitations
===========

The Crate Mesos Framework still has the following limitations and known issues:

* No automatic handling of cluster failures.
* Overall cluster health needs to be monitored separately, using the Crate Admin
UI (running on port ``4200`` at path ``/admin``) or other third party tools.
* The cluster does not automatically resize depending on used resources.
* Although Crate requires a minimum disk size to start, the disk usage is not
monitored inside the framework. This can be done using the Admin UI or plain
SQL.
* A zero-downtime upgrade is not possible.
