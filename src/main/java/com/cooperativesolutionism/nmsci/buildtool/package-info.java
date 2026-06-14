/**
 * 构建期工具：由 Maven antrun 在 {@code prepare-package} 阶段调起的独立 main 类
 * （{@code CalcSourceCodeZipHash}：打包源码 zip 并将其 SHA-256 写入构建产物中的源码包哈希）。
 *
 * <p>本包不属于应用运行期逻辑。它有意保留在源码树内（而非拆为独立模块），
 * 以便其自身也进入上链源码归档，使链上源码包可自校验「该哈希是如何复现的」。</p>
 *
 * <p>antrun 通过全限定类名引用本包的类（见 {@code pom.xml} 的 maven-antrun-plugin 配置），
 * 移动或重命名本包时必须同步更新该处的 {@code classname}。</p>
 */
package com.cooperativesolutionism.nmsci.buildtool;
