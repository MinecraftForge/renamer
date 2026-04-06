/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

import net.minecraftforge.renamer.api.ClassProvider;
import net.minecraftforge.renamer.api.ClassProvider.IClassInfo;
import net.minecraftforge.renamer.api.ClassProvider.IFieldInfo;
import net.minecraftforge.renamer.api.ClassProvider.IMethodInfo;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.INode;
import net.minecraftforge.srgutils.IMappingFile.IParameter;
import net.minecraftforge.srgutils.IRenamer;

import static org.objectweb.asm.Opcodes.*;

class EnhancedRemapper extends Remapper {
	private static final Predicate<String> SRG_PATTERN = Pattern.compile("^(?:[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_)$").asPredicate();
    private final ClassProvider classProvider;
    private final IMappingFile map;
    private final Map<String, String> naiveSrgMap;
    private final Map<String, Optional<MetaClass>> resolved = new ConcurrentHashMap<>();
    private final Consumer<String> log;

    EnhancedRemapper(ClassProvider classProvider, IMappingFile map, Consumer<String> log, boolean naiveSrg) {
    	super(Opcodes.ASM9);
        this.classProvider = classProvider;
        this.map = map;
        this.log = log;
        if (naiveSrg) {
        	this.naiveSrgMap = new HashMap<>();
        	map.rename(new IRenamer() {
        		@Override public String rename(IField value) { return capture(value); }
        		@Override public String rename(IMethod value) { return capture(value); }
        		@Override public String rename(IParameter value) { return capture(value); }
        		private String capture(INode value) {
        			if (SRG_PATTERN.test(value.getOriginal()))
        				naiveSrgMap.put(value.getOriginal(), value.getMapped());
        			return value.getMapped();
        		}
        	});
        } else {
        	this.naiveSrgMap = null;
        }
    }

    // TODO: [Renamer] None of the mapping formats support renaming modules currently.
    @Override
    public String mapModuleName(final String name) {
    	return name;
	}

    @Override
    public String mapAnnotationAttributeName(final String descriptor, final String name) {
        Type type = Type.getType(descriptor);
        if (type.getSort() != Type.OBJECT)
            return name;

        MetaClass cls = getClass(type.getInternalName()).orElse(null);
        if (cls == null)
            return name;

        List<MetaMethod> lst = cls.getMethods(name).orElse(null);
        if (lst == null)
            return name;

        // You should not be able to specify conflicting annotation value names
        // As annotation attributes can't have parameters, and the bytecode doesn't store the descriptor
        // But renamers can be weird so log instead of doing weird things.
        if (lst.size() != 1) {
            for (MetaMethod mtd : lst)
                log.accept("Duplicate Annotation name: " + cls.getName() + " " + mtd.getName() + mtd.getDescriptor() + " -> " + cls.getMapped() + " " + mtd.getName());
            return name;
        }

        return lst.get(0).getMapped();
    }

    private final String naive(String value) {
    	return this.naiveSrgMap == null ? value : this.naiveSrgMap.getOrDefault(value, value);
    }

    @Override
    public String mapBasicInvokeDynamicMethodName(final String name, final String descriptor, final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments) {
    	return naive(name);
	}

    @Override
    public String mapMethodName(final String owner, final String name, final String descriptor) {
    	MetaClass cls = getClass(owner).orElse(null);
    	if (cls == null)
    		return naive(name);
    	MetaMethod mtd = cls.getMethod(name, descriptor).orElse(null);
    	return mtd == null ? naive(name) : mtd.getMapped();
    }

    @Override
    public String mapRecordComponentName(final String owner, final String name, final String descriptor) {
        return mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String descriptor) {
    	MetaClass cls = getClass(owner).orElse(null);
    	if (cls == null)
    		return naive(name);
    	MetaField fld = cls.getField(name, descriptor).orElse(null);
    	return fld == null ? naive(name) : fld.getMapped();
    }

    @Override
    public String mapPackageName(final String name) {
        return this.map.remapPackage(name);
    }

    @Override
    public String map(final String name) {
    	MetaClass cls = getClass(name).orElse(null);
    	return cls == null ? map.remapClass(name) : cls.getMapped();
    }

    public String mapParameterName(final String owner, final String methodName, final String methodDescriptor, final int index, final String paramName) {
    	MetaClass cls = getClass(owner).orElse(null);
    	if (cls == null)
    		return naive(paramName);
    	MetaMethod mtd = cls.getMethod(methodName, methodDescriptor).orElse(null);
    	return mtd == null ? naive(paramName) : mtd.mapParameter(index, paramName);
    }

    private Optional<MetaClass> getClass(String cls) {
        if (cls == null || cls.charAt(0) == '[') // Enums values() function invokes 'clone' on the array type.
            return Optional.empty();             // I'm pretty sure that i'd require stupid hacky JVM to allow native array methods to be remapped.
        Optional<MetaClass> ret = resolved.get(cls);
        if (ret == null) {
            synchronized(cls.intern()) {
                ret = resolved.get(cls);
                if (ret == null) {
                    ret = computeClass(cls);
                    resolved.put(cls, ret);
                }
            }
        }
        return ret;
    }

    private ClassProvider getClassProvider() {
        return this.classProvider;
    }

    private IMappingFile getMap() {
        return this.map;
    }

    private Optional<MetaClass> computeClass(String cls) {
        IClassInfo binaryClass = this.getClassProvider().getClass(cls).orElse(null);
        IMappingFile.IClass mapClass = this.map.getClass(cls);
        if (binaryClass == null && mapClass == null)
            return Optional.empty();
        return Optional.of(new MetaClass(binaryClass, mapClass));
    }

    private class MetaClass {
        private final IClassInfo binaryClass;
        private final IMappingFile.IClass mapClass;
        private final String mappedName;
        private final List<MetaClass> parents;
        private final Map<String, Optional<MetaField>> fields = new ConcurrentHashMap<>();
        private final Collection<Optional<MetaField>> fieldsView = Collections.unmodifiableCollection(fields.values());
        private final Map<String, Optional<MetaMethod>> methods = new ConcurrentHashMap<>();
        private final Collection<Optional<MetaMethod>> methodsView = Collections.unmodifiableCollection(methods.values());
        private final Map<String, Optional<List<MetaMethod>>> methodsByName = new ConcurrentHashMap<>();

        MetaClass(IClassInfo binaryClass, IMappingFile.IClass mapClass) {
            if (binaryClass == null && mapClass == null)
                throw new IllegalArgumentException("Can't pass in both nulls..");

            this.binaryClass = binaryClass;
            this.mapClass = mapClass;
            this.mappedName = mapClass == null ? EnhancedRemapper.this.getMap().remapClass(binaryClass.getName()) : mapClass.getMapped();

            if (binaryClass != null) {
                List<MetaClass> parents = new ArrayList<>();
                EnhancedRemapper.this.getClass(binaryClass.getSuper()).ifPresent(parents::add);
                for (String intf : binaryClass.getInterfaces()) {
                	MetaClass metaClass = EnhancedRemapper.this.getClass(intf).orElse(null);
                	if (metaClass != null)
                		parents.add(metaClass);
                }
                this.parents = Collections.unmodifiableList(parents);

                for (IFieldInfo binaryField : binaryClass.getFields()) {
                	IField mapField = mapClass == null ? null : mapClass.getField(binaryField.getName());
                	MetaField metaField = new MetaField(this, binaryField, mapField);
                	fields.put(metaField.getKey(), Optional.of(metaField));
                }

                for (IMethodInfo binaryMethod : binaryClass.getMethods()) {
                	IMethod mapMethod = mapClass == null ? null : mapClass.getMethod(binaryMethod.getName(), binaryMethod.getDescriptor());
                	MetaMethod metaMethod = new MetaMethod(this, binaryMethod, mapMethod);
                	methods.put(metaMethod.getKey(), Optional.of(metaMethod));
                }
            } else {
                this.parents = Collections.emptyList();
                for (IField mapField : mapClass.getFields()) {
                	MetaField metaField = new MetaField(this, null, mapField);
                	fields.put(metaField.getKey(), Optional.of(metaField));
                }
                for (IMethod mapMethod : mapClass.getMethods()) {
                	MetaMethod metaMethod = new MetaMethod(this, null, mapMethod);
                	methods.put(metaMethod.getKey(), Optional.of(metaMethod));
                }
            }

            for (MetaClass parentCls : parents) {
                for (Optional<MetaField> fldOpt : parentCls.getFields()) {
                    if (!fldOpt.isPresent())
                        continue;

                    MetaField fld = fldOpt.get();
                    Optional<MetaField> existing = this.fields.get(fld.getKey());
                    if (existing == null || !existing.isPresent()) {
                        /* There are some weird cases where a field will be referenced as if it were owned by the current class,
                         * but it needs a field from the parent. So lets follow the linking spec and pull
                         * down fields from parents.
                         *
                         * https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.2
                         */
                        this.fields.put(fld.getKey(), fldOpt);
                    } else {
                        /* Is there any case where we would ever override an existing field?
                         * We don't inherit renames like we do with methods.
                         * This loop is just to populate the parent field lists so we can
                         * have a cache. Trading memory for faster lookups.
                         *
                         * We could nuke this all, and move this code to the getter
                         */
                    }
                }

                for (Optional<MetaMethod> mtdOpt : parentCls.getMethods()) {
                    if (!mtdOpt.isPresent())
                        continue;

                    MetaMethod mtd = mtdOpt.get();
                    /* https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-5.html#jvms-5.4.3.3
                     * According to the spec, it does not check access on super classes, but it checks
                     * on interfaces if it is not ACC_PRIVATE or ACC_STATIC.
                     *
                     * Here are some examples:
                     *   class A {
                     *     static void foo(){}
                     *   }
                     *   class B extends A {
                     *     static void test(){
                     *       foo();   // Compiles to invokestatic B.foo()Z resolved at runtime to A.foo()Z
                     *       A.foo(); // Compiles to invokestatic A.foo()Z
                     *   }
                     *----------------------------------------------------
                     *   interface A {
                     *     static void foo(){}
                     *   }
                     *   class B extends A {
                     *     static void test(){
                     *       foo();   // Compiles error
                     *       A.foo(); // Compiles to invokestatic A.foo()Z
                     *   }
                     *----------------------------------------------------
                     */
                    if (parentCls.isInterface() && !mtd.isInterfaceInheritable())
                        continue;


                    Optional<MetaMethod> existingOpt = this.methods.get(mtd.getKey());
                    if (existingOpt == null || !existingOpt.isPresent()) {
                        /* If there is none existing, then we pull in what we have found from the parents.
                         * This intentionally uses the same object as the parents so that if we have weird edge
                         * cases, we can migrate the mapping transitively.
                         */
                        this.methods.put(mtd.getKey(), mtdOpt);
                    } else {
                        /* If the method exists, lets check if there is a mapping entry in the parent.
                         * If there is, and our current one doesn't have a map entry directly, then
                         * propagate the mapping.
                         *
                         * This should allow weird interactions, such as a parent method satisfying a
                         * interface's method. And that interface's method having a mapping.
                         * ---------------------------------------------------
                         *   This SHOULD work, because we would get A.foo() without mapping
                         *   Then get B.foo() WITH mapping, and set the forced name to the mapping.
                         *
                         *   class A {
                         *     void foo(){}
                         *   }
                         *   interface B {
                         *     void foo(){}
                         *   }
                         *   class C extends A implements B {}
                         *   MD: B/foo()V B/bar()V
                         */
                        MetaMethod existing = existingOpt.get();
                        if (!existing.hasMapping() && !existing.getName().equals(mtd.getMapped())) {
                            if (!existing.getMapped().equals(mtd.getMapped()))
                                log.accept("Conflicting propagated mapping for " + existing + " from " + mtd + ": " + existing.getMapped() + " -> " + mtd.getMapped());
                            existing.setMapped(mtd.getMapped());
                        }
                        /*
                         * Tho, there is one case I can think of that would be weird.
                         * I need to test.
                         * But something like this might break:
                         *   class A {
                         *     void foo(){}
                         *   }
                         *   interface B {
                         *     void foo(){}
                         *   }
                         *   class C extends A implements B {}
                         *   MD: A/foo()V A/bar()V
                         *
                         *   I think this may break because we would most likely want to propagate
                         *   the mapping to the interface.
                         */
                        else if (!mtd.hasMapping() && !mtd.getName().equals(existing.getMapped())) {
                            if (!mtd.getMapped().equals(existing.getMapped()))
                                log.accept("Conflicting propagated mapping for " + mtd + " from " + existing + ": " + mtd.getMapped() + " -> " + existing.getMapped());
                            mtd.setMapped(existing.getMapped());
                        }
                    }
                }
            }
        }

        public String getName() {
            return this.binaryClass != null ? this.binaryClass.getName() : this.mapClass.getOriginal();
        }

        public String getMapped() {
            return this.mappedName;
        }

        public int getAccess() {
            if (this.binaryClass == null)
                return ACC_PRIVATE;
            return this.binaryClass.getAccess();
        }

        public boolean isInterface() {
            return (getAccess() & ACC_INTERFACE) != 0;
        }

        public Collection<Optional<MetaField>> getFields() {
            return this.fieldsView;
        }

        public Optional<MetaField> getField(String name, @Nullable String desc) {
            if (desc == null) {
                return this.fields.computeIfAbsent(name, k -> Optional.empty());
            } else {
                Optional<MetaField> ret = this.fields.get(name + desc);
                if (ret == null) {
                    ret = getField(name, null);
                    this.fields.put(name + desc, ret);
                }
                return ret;
            }
        }

        public Collection<Optional<MetaMethod>> getMethods() {
            return this.methodsView;
        }

        public Optional<MetaMethod> getMethod(String name, String desc) {
            return this.methods.computeIfAbsent(name + desc, k -> Optional.empty());
        }

        Optional<List<MetaMethod>> getMethods(String name) {
            return this.methodsByName.computeIfAbsent(name, k -> {
                List<MetaMethod> mtds = new ArrayList<>();
                for (Optional<MetaMethod> opt : this.getMethods()) {
                    MetaMethod mtd = opt.orElse(null);
                    if (mtd == null || !k.equals(mtd.getName()))
                        continue;
                    mtds.add(mtd);
                }
                return mtds.isEmpty() ? Optional.<List<MetaMethod>>empty() : Optional.of(mtds);
            });
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private class MetaField {
    	private final MetaClass owner;
        private final IFieldInfo binary;
        private final IMappingFile.IField map;
        private final String mappedName;
        private final String key;

        MetaField(MetaClass owner, IFieldInfo binary, IMappingFile.IField field) {
        	this.owner = owner;
            this.binary = binary;
            this.map = field;
            this.mappedName = field == null ? naive(binary.getName()) : field.getMapped();
            this.key = getDescriptor() == null ? getName() : getName() + getDescriptor();
        }

        public String getName() {
            return this.binary != null ? this.binary.getName() : this.map.getOriginal();
        }

        public String getDescriptor() {
            return this.binary != null ? this.binary.getDescriptor() : this.map.getDescriptor();
        }

        public String getMapped() {
            return this.mappedName;
        }

        public String getKey() {
            return this.key;
        }

        @Override
        public String toString() {
            return this.owner.getName() + '/' + getName() + ' ' + getDescriptor();
        }
    }

    private class MetaMethod {
    	private final MetaClass owner;
        private final IMethodInfo binary;
        private final IMappingFile.IMethod map;
        private String mappedName;
        private final String[] params;
        private final String key;
        private final boolean isStatic;

        MetaMethod(MetaClass owner, IMethodInfo binary, IMappingFile.IMethod map) {
        	this.owner = owner;
            this.binary = binary;
            this.map = map;
            this.isStatic =
            	(binary != null && (binary.getAccess() & ACC_STATIC) == ACC_STATIC) ||
        		(map != null && map.getMetadata().containsKey("is_static"));

            if (map != null && !map.getDescriptor().contains("()")) {
                List<String> tmp = new ArrayList<>();
            	if (!this.isStatic)
                    tmp.add("this");

                Type[] args = Type.getArgumentTypes(map.getDescriptor());
                for (int x = 0; x < args.length; x++) {
                    String name = map.remapParameter(x, null);
                    tmp.add(name);
                    if (args[x].getSize() == 2)
                        tmp.add(name);
                }

                this.params = tmp.toArray(new String[tmp.size()]);
            } else {
                this.params = null;
            }
            this.key = getName() + getDescriptor();
        }

        public String getName() {
            return this.binary != null ? this.binary.getName() : this.map.getOriginal();
        }

        public String getDescriptor() {
            return this.binary != null ? this.binary.getDescriptor() : this.map.getDescriptor();
        }

        public String getMapped() {
        	if (mappedName != null)
        		return mappedName;
        	if (map != null)
            	return map.getMapped();
    		return naive(getName());
        }

        public String getKey() {
            return this.key;
        }

        public void setMapped(String name) {
            this.mappedName = name;
        }

        public boolean hasMapping() {
            return this.map != null;
        }

        public int getAccess() {
            if (this.binary == null)
                return ACC_PRIVATE;
            return this.binary.getAccess();
        }

        public boolean isInterfaceInheritable() {
            return isStatic || (getAccess() & (ACC_PRIVATE | ACC_STATIC)) == 0;
        }

        public String mapParameter(int index, String name) {
            String ret = this.params != null && index >= 0 && index < this.params.length ? this.params[index] : name;
            return ret == null ? naive(name) : ret;
        }

        @Override
        public String toString() {
            return this.owner.getName() + '/' + getName() + getDescriptor();
        }
    }
}
