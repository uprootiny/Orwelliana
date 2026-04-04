# NixOps Mastery

NixOps is best understood as a graph compiler for infrastructure:

```text
Deployment := Graph(Node, Resources, Edges)
```

- `Node` is a NixOS configuration.
- `Resource` is a cloud primitive like a VM, disk, or IP.
- `Edge` is dependency wiring: SSH, network, secrets, and ordering.

The practical curriculum is:

1. Build a minimal local deployment to understand state, SSH, and activation.
2. Build a multi-node deployment to force graph thinking.
3. Build a stateful cloud-backed deployment to confront drift and reconciliation.
4. Deliberately break state, SSH, disks, and service configs.
5. Learn the internals: module evaluation, derivations, providers, realization.
6. Integrate secrets via `agenix` or `sops-nix`.
7. Add remote builders and binary caches.
8. Re-explain the entire NixOps pipeline from first principles.
9. Compare NixOps with Colmena and Morph to sharpen the model.

The governing reframing is:

```text
Infrastructure = Pure Function + Effects Boundary
```

- Nix is the pure side.
- NixOps is the effectful interpreter.

This repo’s deployment inventory in [`ops/fleet.edn`](/home/uprootiny/Orwelliana/ops/fleet.edn) is the beginning of that graph-shaped mindset.
