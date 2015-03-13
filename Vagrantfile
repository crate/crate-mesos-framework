# -*- mode: ruby -*-
# vi: set ft=ruby :


VAGRANTFILE_API_VERSION = '2'

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|

    config.vm.box = "chef/centos-7.0"
    config.ssh.forward_agent = true
    config.vm.network "forwarded_port", guest: 5050, host: 5050
    config.vm.network "forwarded_port", guest: 4200, host: 4200
    config.vm.provision "shell", path: "vagrant_up.sh"
end
