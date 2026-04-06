{ resources, ... }:

{
  networks.lab = {
    enable = true;
    address = "192.168.1.0/24";
  };

  node1 = { ... }: {
    deployment = {
      targetEnv = "virtualbox";
      virtualBox.headless = true;
      tags = [ "lab" "web" ];
    };
  };

  node2 = { ... }: {
    deployment = {
      targetEnv = "virtualbox";
      virtualBox.headless = true;
      tags = [ "lab" "client" ];
    };
  };

  gateway = { ... }: {
    deployment = {
      targetEnv = "virtualbox";
      virtualBox.headless = true;
      tags = [ "lab" "gateway" ];
    };
  };
}
