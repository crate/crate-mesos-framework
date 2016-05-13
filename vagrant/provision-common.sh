#!/bin/bash

MESOS_VERSION="0.28.1"
ZOOKEEPER_VERSION="3.4.6"
MARATHON_VERSION="1.1.1"

rpm -Uvh http://repos.mesosphere.io/el/7/noarch/RPMS/mesosphere-el-repo-7-1.noarch.rpm
yum -y install "mesos-$MESOS_VERSION" \
               "mesosphere-zookeeper-$ZOOKEEPER_VERSION" \
               "marathon-$MARATHON_VERSION"
yum -y install golang git bind-utils cyrus-sasl cyrus-sasl-md5

# Clear old config
rm -rf /tmp/mesos
rm -rf /etc/mesos-master/*
rm -rf /etc/mesos-slave/*

echo "zk://mesos-master:2181/mesos" > /etc/mesos/zk

if [[ $(grep "# crate-mesos-framework" /etc/hosts 2> /dev/null) == "" ]] ; then
  echo "Add /etc/hosts entries ..."
  echo "# crate-mesos-framework
# Since we do not have DHCP/DNS we need to set the hostnames manually
192.168.10.100   mesos-master
192.168.10.101   mesos-slave-1
192.168.10.102   mesos-slave-2
192.168.10.103   mesos-slave-3" >> /etc/hosts
fi

# Authentication
# Uncomment and re-run provisioning if you want to test master/slave/framework authentication.
# echo '/etc/mesos/passwd' > /etc/mesos-master/credentials
# echo '/etc/mesos/passwd' > /etc/mesos-slave/credential
# echo 'crammd5' > /etc/mesos-master/authenticators
# echo 'crate foo' > /etc/mesos/passwd

if [ $(id -u crate 2> /dev/null || echo "0") -gt 0 ] ; then
  echo "User crate already exists"
else
  useradd crate -s /bin/bash -m
fi
rm -rf /tmp/crate && mkdir -pv /tmp/crate && chown -R crate.crate /tmp/crate

# Mesos DNS
# Uncomment and re-run provisioning if you want to use Mesos DNS.
# mkdir /home/vagrant/go
# chown vagrant.vagrant /home/vagrant/go
# echo 'export GOPATH=$HOME/go' >> /home/vagrant/.bash_profile
# su - vagrant -c "go get github.com/miekg/dns"
# su - vagrant -c "go get github.com/mesosphere/mesos-dns"
# su - vagrant -c "cd /home/vagrant/go/src/github.com/mesosphere/mesos-dns && go build -o mesos-dns"
# cp /vagrant/vagrant/mesos-dns.json /etc/
# cp /vagrant/vagrant/systemd/mesos-dns.service /etc/systemd/system/
# systemctl enable mesos-dns
# systemctl restart mesos-dns

