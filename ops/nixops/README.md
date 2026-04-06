# NixOps Lab

This directory turns the session’s NixOps discussion into concrete scaffolding.

Files:

- [`network.nix`](/home/uprootiny/Orwelliana/ops/nixops/network.nix): a three-node lab topology
- [`deployment.nix`](/home/uprootiny/Orwelliana/ops/nixops/deployment.nix): deployment metadata for the lab fleet

The intended workflow is:

```bash
nix develop
./scripts/run-nix-lab.sh
nixops create -e ./ops/nixops/network.nix ./ops/nixops/deployment.nix --deployment lab
nixops deploy --deployment lab
```

Design constraints:

- the network is explicit and static
- the gateway is the only node with NAT responsibility
- each node has a clear role
- the topology is small enough to break repeatedly on purpose

This is a learning scaffold, not a production deployment. Its job is to teach:

1. graph thinking
2. state inspection
3. destructive iteration
4. failure recovery
