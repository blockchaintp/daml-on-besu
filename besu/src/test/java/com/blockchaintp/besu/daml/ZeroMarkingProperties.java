package com.blockchaintp.besu.daml;

import com.google.common.collect.Lists;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.RandomDistribution;
import net.jqwik.api.Tuple;
import net.jqwik.api.arbitraries.ByteArbitrary;
import net.jqwik.api.arbitraries.ListArbitrary;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

class ZeroMarkingProperties {

  class TaggedBytes {
    public List<Byte> bytes;
    public String tag;

    public TaggedBytes(List<Byte> bytes, String tag) {
      this.bytes = bytes;
      this.tag = tag;
    }
  }

  class Input256 {
    public UInt256 i;
    boolean hasBeef = false;
    boolean isZeroSpec = false;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeroMarkingProperties.class);
  @Provide
  ByteArbitrary bytes() {
    return Arbitraries.bytes().between(Byte.MIN_VALUE, Byte.MAX_VALUE);
  }


  @Provide
  ByteArbitrary zero() {
    return Arbitraries.bytes().between((byte)0 , (byte)0);
  }

  Arbitrary<List<Byte>> integerStringAsBytes() {
    return Arbitraries.integers().between(1,512)
      .map(i -> {
        var l = new ArrayList<Byte>();
        for (var b: i.toString().getBytes(StandardCharsets.UTF_8)) {
          l.add(b);
        }

        return l;
      });
  }

  @Provide
  Arbitrary<TaggedBytes> noise() {
    return  bytes().list().withSizeDistribution(RandomDistribution.uniform()).ofMaxSize(11)
      .map(b -> new TaggedBytes(b,"noise"));
  }

  @Provide
  ListArbitrary<Byte> zeroRun() {
    return zero().list();
  }

  @Provide
  Arbitrary<TaggedBytes>strayDeadBeef() {
    var beef = Arrays.asList((byte)-34,(byte)-83, (byte)-66, (byte)-17);
    Collections.reverse(beef);
    return Arbitraries.just(new TaggedBytes(beef,"strayDeadBeef"));
  }

  Arbitrary<List<Byte>>strayDead() {
    var beef = Arrays.asList((byte)-66, (byte)-17);
    Collections.reverse(beef);
    return Arbitraries.just(beef);
  }

  @Provide
  Arbitrary<TaggedBytes>deadInt() {
    return integerStringAsBytes().map(b -> {
      List<Byte> both = new ArrayList<Byte>();
      both.addAll(b);
      both.addAll(strayDeadBeef().sample().bytes);
      return new TaggedBytes(both, "deadint");
    });
  }

  @Provide
  Arbitrary<Input256> as256() {

    var dead = Bytes.fromHexString("0xdeadbeef");

    return Arbitraries.frequencyOf(
      Tuple.of(1,strayDeadBeef()),
      Tuple.of(2,deadInt()),
      Tuple.of(2,noise())
    ).list().filter(l -> {
      var size = 0;
      for (var i: l) {
        size = size + i.bytes.size();
      }

      return size <= 32;
    }).map(chunks -> {
      //Reverse and pad our input so it is aligned to the right
      var bytes = new ArrayList<Byte>();
      for (var run : chunks) {
        bytes.addAll(run.bytes);
      }

      while (bytes.size() < 32) {
        bytes.add((byte)0);
      }

      Collections.reverse(bytes);

      var buf = ByteBuffer.allocate(32);
      for (var b: bytes) {
        buf.put(b);
      }

      var ret = new Input256();
      /// If we have a 0xdeadbeef{int} at the right of the buffer, we should get the zeropad
      ret.isZeroSpec = chunks.size() > 0 && chunks.get(0).tag.equals("deadint");
      ret.i = UInt256.fromBytes(Bytes.wrapByteBuffer(buf));
      return ret;
      }
    );
  }

  @Property
  void unmarkZero(@ForAll("as256") Input256 input) {

    var time = System.nanoTime();

    var unmarked = ZeroMarking.unmarkZeros(input.i);

    var time2 = System.nanoTime();

    // If we have added a deadbeef, then it is aligned to it
    if (input.hasBeef && !input.isZeroSpec) {
      assert(unmarked.toHexString().startsWith("0xdeadbeef"))
        : "Buf " + input.i.toHexString() +
        " Umarked " + unmarked.toHexString();
    }
    if (input.isZeroSpec) {
      assert(unmarked.toHexString().startsWith("0x00"))
        : "Buf " + input.i.toHexString() +
        " Unmarked " + unmarked.toHexString();
    }

    LOGGER.info("Scan time {}ms with input bytes {}",
      ((time2 - time) / 1000),
      input.i.toMinimalBytes().toHexString()
    );
  }

}
