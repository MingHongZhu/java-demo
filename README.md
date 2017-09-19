## Linux 5种IO模型

#### 一、概念
网络IO的本质是socket的读取，socket在linux系统被抽象为流，IO可以理解为对流的操作。

对于一次IO访问（以read为例）:
> 第一阶段：通常涉及等待网络上的数据分组到达，然后被复制到内核的某个缓冲区。<br/>
> 第二阶段：把数据从内核缓冲区复制到应用进程缓冲区。

##### 数据流：
磁盘\键盘\鼠标\网卡 ==> 操作系统内核缓冲区 ==> 用户进程缓冲区

##### 同步vs异步
同步和异步关注的是消息通信机制。

> **同步**，在发出调用时，调用者主动等待这个调用的结果。<br/>
> **异步**，调用发出后，调用者不会立刻得到结果。而是在调用发出后，被调用者通过状态、通知来通知调用者，或通过回调函数处理这个调用。

IO中的同步与异步是针对应用程序与内核的交互而言的。

1. **同步**是指用户线程发起 I/O 请求后需要等待或者轮询内核 I/O是否完成，然后再继续。<br/>
1. **异步**过程中用户线程发起 I/O 请求，直接返回，做自己的事情，IO交给内核来处理，当内核 I/O 操作完成后会通知用户线程完成。


##### 阻塞vs非阻塞
阻塞和非阻塞关注的是程序（线程）在等待调用结果（消息/返回值，无所谓同步还是异步）时的状态。
> 阻塞调用是指调用结果返回之前，当前线程会被挂起，一直处于等待消息通知，不能做其他事情。


1. 如果这个线程在等待当前函数返回时，仍在执行其他消息处理，那这种情况就叫做同步非阻塞；<br/>
1. 如果这个线程在等待当前函数返回时，没有执行其他消息处理，而是处于挂起等待状态，那这种情况就叫做同步阻塞；


>- 非阻塞的方式可以明显的提高CPU的利用率，但是也带了另外一种后果就是系统的线程切换增加。增加的CPU执行时间能不能补偿系统的切换成本需要好好评估。<br/>
>- ==阻塞和同步不同==， *同步调用来说，很多时候当前线程可能还是激活的没有被挂起，只是从逻辑上当前函数没有返回而已，此时，这个线程可能也会处理其他的消息。*

##### 上下文切换
> 线程是由CPU进行调度的，单独CPU（多核处理器当作多个单独CPU来识别的）一个时间片内只执行一个线程上下文内的线程，当CPU由执行线程A切换到执行线程B的过程中会发生一些列的操作，这些操作主要有”保存线程A的执行现场“然后”载入线程B的执行现场”，这个过程称之为“上下文切换（context switch）”,这个上下文切换过程并不廉价，如果没有必要，应该尽量减少上下文切换的发生。


#### 二、IO分类
- **同步IO（synchronous IO）**
> 1. 阻塞IO（bloking IO）<br/>
> 1. 非阻塞IO（non-blocking IO）<br/>
> 1. 多路复用IO（multiplexing IO）<br/>
> 1. 信号驱动式IO（signal-driven IO）<br/>

- **异步IO（asynchronous IO）**

==*注：由于signal driven IO在实际中并不常用，所以我这只提及剩下的四种IO Model*。==

![IO分类对比](https://static.oschina.net/uploads/img/201604/21095604_vhHX.png)


1. 同步阻塞

用户线程同步等待结果，线程被挂起，不做其他事情。在linux中，默认情况下所有的socket都是blocking

```
//read 阻塞
byte[] buf = new byte[2048];
int count = socket.getInputStream().read(buf);

// readline()阻塞直到整行读完
BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
String data   = reader.readLine();

```
![同步阻塞](https://static.oschina.net/uploads/img/201604/20150405_VKYH.png)

---

1. 同步非阻塞

用户线程不断询问内核，直到内核缓冲区有数据。在相邻两次询问间隙，用户线程可以释放cpu时间片给其它用户线程线程使用（相当于java 里面wait操作），会带来频繁的上下文切换。

![同步非阻塞](https://static.oschina.net/uploads/img/201604/20152818_DXcj.png)

---

1. 多路复用

多路就是一个线程管理多路IO，此线程还是被阻塞调用，其中一路或几路IO有任何事件发生就通知用户线程处理即可。相当于用户线程只剩下IO的第二阶段，避免传统的 I/O 阻塞。**最大化 CPU 利用率，减少用户线程切换**

![多路复用](https://static.oschina.net/uploads/space/2017/0522/112804_1mhz_2939155.png)

---

1. 信号驱动

给一个IO注册一个信号和信号触发的回调函数，一旦信号被触发，回调函数里读取数据。

例如给 socket注册一个“可读”的信号，当数据来了，可读的时候，信号被触发，执行回调函数从内核cache复制数据到用户空间。
![信号驱动](https://static.oschina.net/uploads/img/201604/21091434_DsZb.png)

---

1. 异步IO

异步IO中，操作系统完成了数据从内核到用户空间的拷贝后，以信号的方式通知用户线程可以下一步操作。省去了用户线程阻塞下来拷贝数据的过程。

![异步IO](https://static.oschina.net/uploads/space/2017/0522/114136_u5gw_2939155.png)



**高性能，且业界普遍使用的方案，也就是后两种。**
I/O 多路复用往往对应 Reactor 模式，异步 I/O 往往对应 Proactor。

Reactor 一般使用 epoll+ 事件驱动 的经典模式，通过 分治 的手段，把耗时的网络连接、安全认证、编码等工作交给专门的线程池或者进程去完成，然后再去调用真正的核心业务逻辑层，这在 *nix 系统中被广泛使用。

著名的 Redis、Nginx、Node.js 的 Socket I/O 都用的这个，Java 的 NIO 框架 Netty 也是，Spark 2.0 RPC 所依赖的同样采用了 Reactor 模式。



#### 三、JAVA NIO
流是单向的，通道是双向的。
IO vs NIO：

IO | NIO | 说明
------------ | ------------- | -------------
面向流 | 面向缓冲 | Java IO每次从流中读字节，直至读取所有字节，它们没有被缓存在任何地方。 java NIO数据读取到一个缓冲区，需要时可在缓冲区中前后移动。
阻塞IO | 非阻塞IO | java IO线程调用read() 或 write()时，该线程被阻塞，直到有一些数据被读取，或数据完全写入。该线程在此期间不能再干任何事情了。java NIO线程通常将非阻塞IO的空闲时间用于在其它通道上执行IO操作
无 | 选择器 | 无
