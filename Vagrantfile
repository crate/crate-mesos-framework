# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|

    # shared configuration
    config.vm.box = "bento/centos-7.2"

    # mesos master with zookeeper
    config.vm.define "mesos-master" do |host|
        host.vm.provider "virtualbox" do |v|
            v.memory = 1024
            v.cpus = 1
        end
        host.vm.hostname = "mesos-master"
        host.vm.network :private_network, ip: "192.168.10.100"
        host.vm.provision "shell", path: "vagrant/provision-common.sh"
        host.vm.provision :shell, :inline => <<-SCRIPT
            echo "$(ifconfig enp0s8 | grep 'inet ' | awk '{print $2}')" | sudo tee /etc/mesos-master/ip
            echo "$(hostname)" | sudo tee /etc/mesos-master/hostname
            echo "1" | sudo tee /var/lib/zookeeper/myid
            echo "1" | sudo tee /etc/mesos-master/quorum
            echo "/var/lib/mesos/master" | sudo tee /etc/mesos-master/work_dir
            echo "crate-mesos-demo" | sudo tee /etc/mesos-master/cluster
            sudo systemctl disable mesos-slave
            sudo systemctl stop mesos-slave
            sudo systemctl enable zookeeper
            sudo systemctl restart zookeeper
            sudo systemctl enable mesos-master
            sudo systemctl restart mesos-master
            sudo systemctl enable marathon
            sudo systemctl restart marathon
        SCRIPT
    end

    # mesos slaves
    (1..3).each do |i|
        config.vm.define "mesos-slave-#{i}" do |host|
            host.vm.provider "virtualbox" do |v|
                v.memory = 2048
                v.cpus = 2
            end
            host.vm.hostname = "mesos-slave-#{i}"
            host.vm.network :private_network, ip: "192.168.10.#{100+i}"
            host.vm.provision "shell", path: "vagrant/provision-common.sh"
            host.vm.provision :shell, :inline => <<-SCRIPT
                echo "$(hostname)" | sudo tee /etc/mesos-slave/hostname
                echo "$(ifconfig enp0s8 | grep 'inet ' | awk '{print $2}')" | sudo tee /etc/mesos-slave/ip
                echo "mesos" | sudo tee /etc/mesos-slave/containerizers
                echo "10mins" | sudo tee /etc/mesos-slave/executor_registration_timeout
                echo "ports(*):[31000-31099, 31101-32000, 4000-4999]" | sudo tee /etc/mesos-slave/resources
                sudo systemctl disable mesos-master
                sudo systemctl stop mesos-master
                sudo systemctl disable marathon
                sudo systemctl stop marathon
                sudo systemctl disable zookeeper
                sudo systemctl stop zookeeper
                sudo systemctl enable mesos-slave
                sudo systemctl restart mesos-slave
            SCRIPT
        end
    end

    config.ssh.forward_agent = true
end
