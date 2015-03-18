#!/bin/bash

rpm -Uvh http://repos.mesosphere.io/el/7/noarch/RPMS/mesosphere-el-repo-7-1.noarch.rpm
yum -y install mesos
yum -y install mesosphere-zookeeper
yum -y install docker

echo 1 > /var/lib/zookeeper/myid
echo 'zk://127.0.0.1:2181/mesos' > /etc/mesos/zk
echo 'docker,mesos' > /etc/mesos-slave/containerizers
echo '10mins' > /etc/mesos-slave/executor_registration_timeout
echo 'ports(*):[31000-31099, 31101-32000, 4200-4399]' > /etc/mesos-slave/resources

systemctl enable docker
systemctl start docker
systemctl start zookeeper
systemctl start mesos-master
systemctl start mesos-slave
