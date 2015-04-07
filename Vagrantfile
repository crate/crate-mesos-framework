# -*- mode: ruby -*-
# vi: set ft=ruby :


VAGRANTFILE_API_VERSION = '2'

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|

    config.vm.box = "chef/centos-7.0"
    config.ssh.forward_agent = true
    config.vm.network "forwarded_port", guest: 5050, host: 5050
    config.vm.network "forwarded_port", guest: 8080, host: 8080
    config.vm.network "forwarded_port", guest: 5051, host: 5051
    config.vm.network "forwarded_port", guest: 2181, host: 2181
    config.vm.network "forwarded_port", guest: 4200, host: 4200
    config.vm.network "forwarded_port", guest: 4040, host: 4040
    config.vm.provision "shell", path: "vagrant/vagrant_up.sh"

    config.vm.provider "virtualbox" do |v|
        v.memory = 3072
        v.cpus = 2
    end
end
