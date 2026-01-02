package io.github.yetyman.vulkan.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for building MemorySegment arrays
 */
public class VkArrayBuilder {
    
    public static AddressArrayBuilder addresses() {
        return new AddressArrayBuilder();
    }
    
    public static IntArrayBuilder ints() {
        return new IntArrayBuilder();
    }
    
    public static class AddressArrayBuilder {
        private final List<MemorySegment> addresses = new ArrayList<>();
        
        public AddressArrayBuilder add(MemorySegment address) {
            addresses.add(address);
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            if (addresses.isEmpty()) return MemorySegment.NULL;
            
            MemorySegment array = arena.allocate(ValueLayout.ADDRESS, addresses.size());
            for (int i = 0; i < addresses.size(); i++) {
                array.setAtIndex(ValueLayout.ADDRESS, i, addresses.get(i));
            }
            return array;
        }
        
        public int count() {
            return addresses.size();
        }
    }
    
    public static class IntArrayBuilder {
        private final List<Integer> ints = new ArrayList<>();
        
        public IntArrayBuilder add(int value) {
            ints.add(value);
            return this;
        }
        
        public MemorySegment build(Arena arena) {
            if (ints.isEmpty()) return MemorySegment.NULL;
            
            MemorySegment array = arena.allocate(ValueLayout.JAVA_INT, ints.size());
            for (int i = 0; i < ints.size(); i++) {
                array.setAtIndex(ValueLayout.JAVA_INT, i, ints.get(i));
            }
            return array;
        }
        
        public int count() {
            return ints.size();
        }
    }
}