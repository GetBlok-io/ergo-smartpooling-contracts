# The Command Box
The command box represents a box protected by a contract whose propositional bytes are in the pool operators
section of the metadata box. The command box serves as a template for the next metadata box to copy from.
The command box has the exact same register types as the metadata box, and each register of the command box
must be exactly copied into the new metadata box created during the distribution tx.

The command box may be protected by any contract, this ensures that payment mechanisms involving command boxes
can be easily changed without destroying any payment history.

## Usages
The command box is mainly used to verify that the metadata box is being spent by the correct party(or parties).
Because the command box may have any script, we can use the command box to ensure that specific requirements
are met in order for a distribution tx to occur. For the simple holding contract, the command box is also
used to verify that payment information is not destroyed in the case of distribution tx that contains
no holding boxes.

The simplest version of a command box contract may be a P2PK contract that ensures that the tx must be signed
by the owner of the private key. In the context of the command box, this ensures that every distribution tx
will have register values picked by the owner of the secret key.

## Custom Command Boxes
More complex versions of command contracts may also be created. The Command Box is technically an optional payment and it is not a requirement that
the money it holds is redistributed. For example, if we have a SmartPool with a
Pool Operator defined as a simple P2PK address's propBytes, what happens with
the Command Box's funds is up to the Pool Operator. This also means that each
consensus transaction would have to be signed by the pool operator, since that is
a requirement for any P2PK script. If the Pool Operator wished, they could simply
create an Output Box in each consensus transaction that has the same value as the
Command Box and is also protected by their P2PK script. In this way, no funds are injected
into the SmartPool.

When funds *are* injected using the command box, the possibilities are endless.
Imagine a SmartPool which wishes to add an automatic block bounty, where the miner who finds
the block gets an extra reward of 5 ERG. We may create a Command Box script that takes some miner's P2PK script bytes as an input
and requires that a transaction spending the Command Box has an output box of 5 ERG protected by the miner's script.
When a block is found, we may pass the block finder's propBytes to the Command Box script and create
a Command Box with a value of 5 ERG. The SmartPool's consensus transaction will now require that a
box with the Block Bounty of 5 ERG be created, in addition to the other boxes created during a standard
consensus. The Block Bounty box will be protected by the Block Finder's P2PK script, as was specified
in the Command Box Contract.

We may also use the command box to create a SmartPool governed by vote. Let us say we have command box
that requires 50% of the members in the Metadata Box to submit a vote. We define a vote to be some