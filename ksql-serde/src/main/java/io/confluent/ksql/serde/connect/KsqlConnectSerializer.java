/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.serde.connect;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.storage.Converter;

public class KsqlConnectSerializer implements Serializer<Struct> {

  private final Schema physicalSchema;
  private final DataTranslator translator;
  private final Converter converter;
  private final Function<Struct, Object> preprocessor;

  public KsqlConnectSerializer(
      final Schema physicalSchema,
      final Function<Struct, Object> preprocessor,
      final DataTranslator translator,
      final Converter converter
  ) {
    this.physicalSchema = Objects.requireNonNull(physicalSchema, "physicalSchema");
    this.translator = Objects.requireNonNull(translator, "translator");
    this.converter = Objects.requireNonNull(converter, "converter");
    this.preprocessor = Objects.requireNonNull(preprocessor, "preprocessor");
  }

  @Override
  public byte[] serialize(final String topic, final Struct data) {
    if (data == null) {
      return null;
    }

    final Object connectRow = translator.toConnectRow(data);

    final Object toSerialize = preprocessor.apply((Struct)connectRow);

    try {
      return converter.fromConnectData(topic, physicalSchema, toSerialize);
    } catch (final Exception e) {
      throw new SerializationException(
          "Error serializing row to topic " + topic + " using Converter API", e);
    }
  }

  @Override
  public void configure(final Map<String, ?> map, final boolean b) {
  }

  @Override
  public void close() {
  }
}
