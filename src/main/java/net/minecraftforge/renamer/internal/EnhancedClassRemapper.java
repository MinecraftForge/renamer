/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.internal;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;

class EnhancedClassRemapper extends ClassRemapper {
    private final EnhancedRemapper remapper;
    private final RenamingTransformer transformer;

    EnhancedClassRemapper(ClassVisitor classVisitor, EnhancedRemapper remapper, RenamingTransformer transformer) {
        super(classVisitor, remapper);
        this.remapper = remapper;
        this.transformer = transformer;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String mname, final String mdescriptor, final String msignature, final String[] exceptions) {
        //System.out.println("Method: " + className + '/' + mname + mdescriptor);
        String remappedDescriptor = remapper.mapMethodDesc(mdescriptor);
        MethodVisitor methodVisitor = cv.visitMethod(access, remapper.mapMethodName(className, mname, mdescriptor), remappedDescriptor, remapper.mapSignature(msignature, false), exceptions == null ? null : remapper.mapTypes(exceptions));
        if (methodVisitor == null)
            return null;

        // There is no bytecode storage for abstract parameters, so we store them locally in a special file fernflower can see
        if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
            renameAbstract(access, mname, mdescriptor);

        // We no longer have to map lambas as Upstream has now added support: https://gitlab.ow2.org/asm/asm/-/commit/124a45002ba09a6bf6fc6ce4a428321737f466f1
        // However we still have to map local variable names
        return new MethodRemapper(methodVisitor, remapper) {
            @Override
            public void visitLocalVariable(final String pname, final String pdescriptor, final String psignature, final Label start, final Label end, final int index) {
                super.visitLocalVariable(EnhancedClassRemapper.this.remapper.mapParameterName(className, mname, mdescriptor, index, pname), pdescriptor, psignature, start, end, index);
            }
        };
    }

    private void renameAbstract(int access, String name, String descriptor) {
        Type[] types = Type.getArgumentTypes(descriptor);
        if (types.length == 0)
            return;

        List<String> names = new ArrayList<>();
        int i = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : types) {
            names.add(remapper.mapParameterName(className, name, descriptor, i, "var" + i));
            i += type.getSize();
        }

        transformer.storeNames(
            remapper.mapType(className),
            remapper.mapMethodName(className, name, descriptor),
            remapper.mapMethodDesc(descriptor),
            names
        );
    }
}
