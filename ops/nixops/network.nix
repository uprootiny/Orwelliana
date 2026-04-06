{ lib, ... }:

{
  description = "orwelliana-lab";

  node1 = { config, pkgs, ... }: {
    config = lib.mkMerge [
      {
        networking.hostName = "node1";
        networking.domain = "lab.local";
        networking.useDHCP = false;
        networking.interfaces.enp0s3.ipv4.addresses = [
          {
            address = "192.168.1.10";
            prefixLength = 24;
          }
        ];
        networking.defaultGateway = "192.168.1.1";
        services.openssh.enable = true;
        services.nginx.enable = true;
        services.nginx.virtualHosts."node1.lab.local" = {
          root = "/var/www/node1";
        };
        environment.systemPackages = with pkgs; [
          curl
          htop
        ];
      }
    ];
  };

  node2 = { config, pkgs, ... }: {
    config = lib.mkMerge [
      {
        networking.hostName = "node2";
        networking.domain = "lab.local";
        networking.useDHCP = false;
        networking.interfaces.enp0s3.ipv4.addresses = [
          {
            address = "192.168.1.11";
            prefixLength = 24;
          }
        ];
        networking.defaultGateway = "192.168.1.1";
        services.openssh.enable = true;
        environment.systemPackages = with pkgs; [
          curl
          wget
        ];
      }
    ];
  };

  gateway = { config, pkgs, ... }: {
    config = lib.mkMerge [
      {
        networking.hostName = "gateway";
        networking.domain = "lab.local";
        networking.useDHCP = false;
        networking.interfaces.enp0s3.ipv4.addresses = [
          {
            address = "192.168.1.1";
            prefixLength = 24;
          }
        ];
        networking.nat.enable = true;
        networking.nat.forwardPorts = [
          {
            sourcePort = 80;
            destination = "192.168.1.10:80";
          }
        ];
        networking.firewall.enable = true;
        networking.firewall.allowedTCPPorts = [ 80 22 ];
        services.openssh.enable = true;
        environment.systemPackages = with pkgs; [
          tcpdump
        ];
      }
    ];
  };
}
