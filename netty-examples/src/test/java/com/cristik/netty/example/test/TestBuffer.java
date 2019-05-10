package com.cristik.netty.example.test;

import org.junit.Test;

import java.nio.IntBuffer;

/**
 * @author cristik
 */
public class TestBuffer {

    public static void main(String[] args) {
        //Buffer很恶心你慢慢研究就会发现他的恶心之处  ， 好了 下面好好享受Buffer
        // 1 基本操作
        //创建指定长度的缓冲区
        IntBuffer buf = IntBuffer.allocate(10);
        buf.put(13);// position位置：0 - > 1
        buf.put(21);// position位置：1 - > 2
        buf.put(35);// position位置：2 - > 3
        buf.put(99);// position位置：2 - > 3
        //把位置复位为0，也就是position位置：3 - > 0   建议每次put()完数据都flip()一下。
        buf.flip();
        System.out.println("使用flip复位：" + buf);
        System.out.println("容量为: " + buf.capacity());   //容量一旦初始化后不允许改变（warp方法包裹数组除外）
        System.out.println("限制为: " + buf.limit());      //由于只装载了三个元素,所以可读取或者操作的元素为3 则limit=3


        System.out.println("获取下标为1的元素：" + buf.get(1));
        System.out.println("get(index)方法，position位置不改变：" + buf);
        buf.put(1, 4); //下标为1的参数变为4    12 -->  4
        buf.flip();
        System.out.println("put(index, change)方法，position位置不变：" + buf);;

        for (int i = 0; i < buf.limit(); i++) {
            //调用get方法会使其缓冲区位置（position）向后递增一位
            System.out.print(buf.get() + "\t");
        }
        System.out.println("\n"+"buf对象遍历之后为: " + buf);

        // 2 wrap方法使用
        /**
         //  wrap方法会包裹一个数组: 一般这种用法不会先初始化缓存对象的长度，因为没有意义，最后还会被wrap所包裹的数组覆盖掉。
         //  并且wrap方法修改缓冲区对象的时候，数组本身也会跟着发生变化。
         int[] arr = new int[]{1,2,5};
         IntBuffer buf1 = IntBuffer.wrap(arr);
         System.out.println(buf1);

         IntBuffer buf2 = IntBuffer.wrap(arr, 0 , 2);
         //这样使用表示容量为数组arr的长度，但是可操作的元素只有实际进入缓存区的元素长度
         System.out.println(buf2);
         */


        // 3 其他方法
        /**
         IntBuffer buf1 = IntBuffer.allocate(10);
         int[] arr = new int[]{1,2,5};
         buf1.put(arr);
         System.out.println(buf1);
         //一种复制方法
         IntBuffer buf3 = buf1.duplicate();
         System.out.println(buf3);

         //设置buf1的位置属性
         //buf1.position(0);
         buf1.flip();
         System.out.println(buf1);

         System.out.println("可读数据为：" + buf1.remaining());

         int[] arr2 = new int[buf1.remaining()];
         //将缓冲区数据放入arr2数组中去
         buf1.get(arr2);
         for(int i : arr2){
         System.out.print(Integer.toString(i) + ",");
         }
         */
    }

    @Test
    public void testBuffer2() {
        IntBuffer buffer = IntBuffer.allocate(20);
        buffer.put(new int[]{1, 2, 3,});
        buffer.put(new int[]{4, 2, 3,});
        buffer.flip();
        buffer.flip();
        System.out.println(buffer);
    }
}
