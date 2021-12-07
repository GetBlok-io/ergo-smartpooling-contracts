# The Metadata Box

The metadata box represents a crucial part of every distribution transaction. We may consider the metadata
box to be the "head" of the smart pool. Any time information about a smart pool is retrieved from on-chain
it will be from one of the registers on the metadata box. The metadata box also ensures that it may only 
be spent with a valid command box under its pool operators register.

## Register 4: Members AVL Tree
In register 4 of the command box, we will store an AVL tree that maps the proposition bytes of members
to various information about each member, stored as a `Coll[Long]`. The AVL tree will allow fast retrieval of information along with
the ability to store large amounts of info. This ensures fast computation time and lower fees for distribution
transactions.

Each member in the member tree will have information stored as a long. The following information is required
inside each member's collection of longs:
- Elem 0: Prop Bytes of member stored as a long
- Elem 1: Name/Address of member, encoded in UTF-8 and stored as a long
- Elem 2: Last Share number of member, obtained in distribution transaction from holding box
- Elem 3: Last Payment of member, obtained in distribution transaction from holding box
- Elem 4: Minimum payment of member, obtained in distribution transaction from command box

Elements 4+ are not checked in the standard distribution transaction. They may be used by the command
box for different payment mechanisms.

## Register 5: Pool Fees
Register 5 of the metadata box stores the pool fees. Pool fees are a `Coll[(Coll[Byte], Int)]` that map
the propositional bytes of various fee addresses to some percentage of the total holding value. Pool fees
are calculated to be `(x/1000)*TOTAL_HOLDING_VALUE` where x is the integer number that the proposition
bytes of the fee map to. This means that each pool fee can take a minimum of 0.1% of the total holding value.

## Register 6: Pool Info
The metadata box's info and parameters can be seen in register 6, which holds a `Coll[BigInt]` that gives
information about the Smart Pool's current state. Here is the required information inside each pool info
collection:
- Elem 0: The current pool epoch. This value must increase by 1 during every distribution transaction.
- Elem 1: The epoch height. This is the height that the current epoch started. This height must update everytime
distribution transaction occurs.
- Elem 2: The creation height. This is the height at which epoch 0 started for this Smart Pool. This value
must stay the same in each distribution transaction.
- Elem 3: The creation box id. This is the id of the first metadata box with epoch 0. This value
must stay the same in each distribution transaction.
- Elem 4: The minimum epoch length. This is the minimum length of each epoch. This number ensures that a
distribution transaction can only be performed after a certain number of blocks have been mined.
- Elem 5: The forking flag. If this number is 0, forking is disabled. If it is not 0, then forking is allowed
and this smart pool must create two identical metadata boxes in this distribution transaction.
- Elem 6. The destruction flag. If this number is 0, destruction is disabled. If it is not 0, then this
smart pool may destroy its metadata box in the next distribution transaction. Destruction rules are
determined by the command box.

Elem 7+ are not checked in the distribution transaction. They may be used by the command box to store
other info for other payment mechanisms.

## Register 7: Pool Operators
The pool operators collection is stored in register 7. It is a `Coll[(Coll[Byte], Coll[Byte])]` that maps
the propositional bytes of command boxes to the address. The address field is not checked on-chain and is
only there so that people can verify that the prop bytes stored in the pool operators box is the prop
bytes for the address that it is mapped to.

The pool operators list is used by the holding box and by the metadata box to verify that the command box
being used is under a valid script. 