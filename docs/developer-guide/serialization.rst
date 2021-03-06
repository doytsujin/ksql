.. _ksql_serialization:

KSQL Serialization
==================

=========================
Controlling serialization
=========================

KSQL offers several mechanisms for controlling serialization and deserialization.

The primary mechanism is by choosing the serialization format when you create
a stream or table and specify the ``VALUE_FORMAT`` in the ``WITH`` clause.

.. code:: sql

    CREATE TABLE x (F0 INT, F1 STRING) WITH (VALUE_FORMAT='JSON', ...);

For more information on the formats that KSQL supports, see :ref:`ksql_formats`.

KSQL provides some additional configuration that allows serialization to be controlled:

-------------------------
Single field (un)wrapping
-------------------------

When KSQL serializes a row into a Kafka record, the key fields are serialized into the key of the
Kafka record, and any value fields are serialized into the value. By default, when the value has only a
single field, KSQL serializes the single field within an outer JSON object or Avro
record. For example, consider the statement:

.. code:: sql

    CREATE STREAM x (f0 INT, f1 STRING) WITH (VALUE_FORMAT='JSON', ...);
    CREATE STREAM y AS SELECT f0 FROM x;

The second statement defines a stream with only a single field in the value, named ``f0``, and uses the
JSON format. When KSQL writes out the result to Kafka, it persists a row with field ``f0`` set to the value
``10`` as a JSON object:

.. code:: json

   {
      "F0": 10
   }

If you want the value to be serialized without the outer JSON object or Avro record, set
:ref:`ksql_persistence_serialization_wrap_single_values` to ``false`` before running the statement.

.. code:: sql

    SET 'ksql.persistence.serialization.wrap.single.values'='false';
    CREATE STREAM y AS SELECT f0 FROM x;

When this setting is turned off, the output isn't nested. In this example, it's a JSON
number:

... code json

    10

.. _ksql_formats

=======
Formats
=======

KSQL currently supports three serialization formats:

*. ``DELIMITED`` supports comma separated values. See :ref:`delimited_format` below.
*. ``JSON`` supports JSON values. See :ref:`json_format` below.
*. ``AVRO`` supports AVRO serialized values. See :ref:`avro_format` below.


.. _delimited_format

---------
DELIMITED
---------

The ``DELIMITED`` format supports comma separated values.

The serialized object should be a Kafka-serialized string, which will be split into columns.

For example, given a KSQL statement such as:

.. code:: sql

    CREATE STREAM x (ID BIGINT, NAME STRING, AGE INT) WITH (VALUE_FORMAT='DELIMITED', ...);

KSQL splits a value of ``120, bob, 49`` into the three fields with ``ID`` of ``120``,
``NAME`` of ``bob`` and ``AGE`` of ``49``.

This data format supports all KSQL :ref:`data types <data-types>` except ``ARRAY``, ``MAP`` and
``STRUCT``.

.. _json_format

----
JSON
----

The ``JSON`` format supports JSON values.

The JSON format supports all of KSQL's ref:`data types <data-types>`. As JSON does not itself
support a map type, KSQL serializes ``MAP``s as JSON objects.  Because of this the JSON format can
only support ``MAP`` objects that have ``STRING`` keys.

The serialized object should be a Kafka-serialized string containing a valid JSON value. The format
supports JSON objects and top-level primitives, arrays and maps. See below for more info.

JSON Objects
------------

Values that are JSON objects are probably the most common.

For example, given a KSQL statement such as:

.. code:: sql

    CREATE STREAM x (ID BIGINT, NAME STRING, AGE INT) WITH (VALUE_FORMAT='JSON', ...);

And a JSON value of:

.. code:: json

       {
         "id": 120,
         "name": "bob",
         "age": "49"
       }

KSQL deserializes the JSON object's fields into the corresponding fields of the stream.

Top-level Primitives
--------------------

The JSON format supports reading top-level JSON primitives, but can if the target schema contains
a single field of a compatible type.

For example, given a KSQL statement with only a single field in the value schema:

.. code:: sql

    CREATE STREAM x (ID BIGINT) WITH (VALUE_FORMAT='JSON', ...);

And a JSON value of:

.. code:: json

       10

KSQL deserializes the JSON primitive ``10`` into the ``ID`` field of the stream.

However, if the value schema contains multiple fields, for example:

.. code:: sql

    CREATE STREAM x (ID BIGINT, NAME STRING) WITH (VALUE_FORMAT='JSON', ...);

Deserialization fails, because it's ambiguous as to which field the primitive value should be
deserialized into.

Top-level Arrays
----------------

The JSON format supports reading top-level JSON arrays, but only if the target schema contains a
single field of a compatible type.

For example, given a KSQL statement with only a single array field in the value schema:

.. code:: sql

    CREATE STREAM x (REGIONS ARRAY<STRING>) WITH (VALUE_FORMAT='JSON', ...);

And a JSON value of:

.. code:: json

       [
          "US",
          "EMEA"
       ]

KSQL deserializes the JSON array into the ``REGIONS`` field of the stream.

However, if the value schema contains multiple fields, for example:

.. code:: sql

    CREATE STREAM x (REGIONS ARRAY<STRING>, NAME STRING) WITH (VALUE_FORMAT='JSON', ...);

Deserialization fails, because it's ambiguous as to which field the primitive value should be
deserialized into.

Top-level Maps
--------------

.. tip:: When you deserialize JSON objects into a single ``MAP`` field, ensure the name of the
         field within the KSQL statement doesn't conflict with any of the keys in the map.
         Any conflict can lead to undesirable deserialization artifacts because KSQL treats the
         value as a normal JSON object, not as a map.

The JSON format supports reading a JSON object as a ``MAP``, but only if the target schema contains
a single field of a compatible type.

For example, given a KSQL statement with only a single map field in the value schema:

.. code:: sql

    CREATE STREAM x (PROPS MAP<STRING, STRING>) WITH (VALUE_FORMAT='JSON', ...);

And a JSON value of:

.. code:: json

       {
          "nodeCount": 10,
          "region": "us-12",
          "userId": "peter"
       }

KSQL deserializes the JSON map into the ``PROPS`` field of the stream.

However, if the value schema contains multiple fields, for example:

.. code:: sql

    CREATE STREAM x (PROPS MAP<STRING, STRING>, NAME STRING) WITH (VALUE_FORMAT='JSON', ...);

Deserialization fails, because it's ambiguous as to which field the primitive value should be
deserialized into.

A further potential ambiguity exists when working with top-level maps, when any of the keys of the
value match the name of the singular field in the target schema.

For example, given:

.. code:: sql

    CREATE STREAM x (PROPS MAP<STRING, STRING>) WITH (VALUE_FORMAT='JSON', ...);

And a JSON value of:

.. code:: json

       {
          "props": {
             "x": "y"
          },
          "region": "us-12",
          "userId": "peter"
       }

Deserializing the value is ambiguous: does KSQL deserialize to a top-level map or object? KSQL
deserializes the value as a JSON object, meaning ``PROPS`` is populated with an entry ``x -> y``
only.  Avoid this kind of ambiguity by ensuring the name of the field using in the KSQL statement
never clashes with a property name within the json object, or that the target schema contains more
than a single field.

.. _avro_format

----
Avro
----

The ``AVRO`` format supports Avro binary serialized of all of KSQL's ref:`data types <data-types>`
including records and top-level primitives, arrays and maps.

The format requires KSQL to be configured to store and retrieve the Avro schemas from the |sr-long|.
For more information, see :ref:`install_ksql-avro-schema`.

------------
Avro Records
------------

Avro records can be deserialized into matching KSQL schemas.

For example, given a KSQL statement such as:

.. code:: sql

    CREATE STREAM x (ID BIGINT, NAME STRING, AGE INT) WITH (VALUE_FORMAT='JSON', ...);

And an Avro record serialized with the schema:

.. code:: json

       {
         "type": "record",
         "namespace": "com.acme",
         "name": "UserDetails",
         "fields": [
           { "name": "id", "type": "long" },
           { "name": "name", "type": "string" }
           { "name": "age", "type": "int" }
         ]
       }

KSQL deserializes the Avro record's fields into the corresponding fields of the stream.

-------------------------------------
Top-level primitives, arrays and maps
-------------------------------------

The Avro format supports reading top-level primitives, arrays and maps, but only if the target
schema contains a single field of a compatible type.

For example, given a KSQL statement with only a single field in the value schema:

.. code:: sql

    CREATE STREAM x (ID BIGINT) WITH (VALUE_FORMAT='JSON', ...);

And an Avro value serialized with the schema:

.. code:: json

       {
         { "type": "long" }
       }

KSQL can deserialize the values into the ``ID`` field of the stream.

However, if the value schema contains multiple fields, for example:

.. code:: sql

    CREATE STREAM x (ID BIGINT, NAME STRING) WITH (VALUE_FORMAT='JSON', ...);

Deserialization fails, because it's ambiguous as to which field the primitive value should be
deserialized into.
