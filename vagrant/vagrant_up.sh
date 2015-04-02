#!/bin/bash

rpm -Uvh http://repos.mesosphere.io/el/7/noarch/RPMS/mesosphere-el-repo-7-1.noarch.rpm
yum -y install marathon
yum -y install mesos
yum -y install mesosphere-zookeeper
yum -y install docker
yum -y install /usr/bin/dig
yum -y install cyrus-sasl
yum -y install cyrus-sasl-md5


echo 1 > /var/lib/zookeeper/myid
echo 'zk://127.0.0.1:2181/mesos' > /etc/mesos/zk
echo 'docker,mesos' > /etc/mesos-slave/containerizers
echo '10mins' > /etc/mesos-slave/executor_registration_timeout
echo 'ports(*):[31000-31099, 31101-32000, 4200-4399]' > /etc/mesos-slave/resources
echo 'crate-demo' > /etc/mesos-master/cluster
echo '/etc/mesos/passwd' > /etc/mesos-master/credentials
echo '/etc/mesos/passwd' > /etc/mesos-slave/credential
echo 'crammd5' > /etc/mesos-master/authenticators
echo 'crate foo' > /etc/mesos/passwd



yum -y install golang
yum -y install git
mkdir /home/vagrant/go
chown vagrant.vagrant /home/vagrant/go
echo 'export GOPATH=$HOME/go' >> /home/vagrant/.bash_profile
su - vagrant -c "go get github.com/miekg/dns"
su - vagrant -c "go get github.com/mesosphere/mesos-dns"
su - vagrant -c "cd /home/vagrant/go/src/github.com/mesosphere/mesos-dns && \
    go build -o mesos-dns"

cp /vagrant/vagrant/mesos-dns.json /etc/
cp /vagrant/vagrant/systemd/mesos-dns.service /etc/systemd/system/
systemctl enable mesos-dns
systemctl start mesos-dns

gpasswd -a vagrant docker
systemctl enable docker
systemctl start docker
systemctl start zookeeper
systemctl start mesos-master
systemctl start mesos-slave
systemctl start marathon
