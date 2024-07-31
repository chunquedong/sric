//
// Copyright (c) 2024, chunquedong
// Licensed under the Academic Free License version 3.0
//
package sc2.compiler.ast;

import sc2.compiler.ast.AstNode.TypeDef;
import java.util.ArrayList;

/**
 *
 * @author yangjiandong
 */
public class Type extends AstNode {
    public String namespace;
    public String name;
    public ArrayList<Type> genericArgs;
    
    //** array size or primitive type sized. the Int32 size is 32
    public int size;
  
    //** Is this is a nullable type (marked with trailing ?)
    public boolean isNullable = false;
    
    public static enum PointerType {
        own, ref, raw, weak
    };
    
    public boolean isConst = false;
  
    public TypeDef resolvedTypeDef;
    
    public boolean isPointer() {
        return false;
    }
    
    public static Type voidType(Loc loc) {
        Type type = new Type();
        type.loc = loc;
        return type;
    }
    
    public static Type listType(Loc loc, Type elemType) {
        Type type = new Type();
        type.loc = loc;
        return type;
    }
    
    public static Type arrayRefType(Loc loc, Type elemType) {
        Type type = new Type();
        type.loc = loc;
        return type;
    }
    
    public static Type arrayType(Loc loc, Type elemType, int size) {
        Type type = new Type();
        type.loc = loc;
        type.size = size;
        return type;
    }
    
    public static Type pointerType(Loc loc, Type elemType, PointerType pointerType) {
        Type type = new Type();
        type.loc = loc;
        return type;
    }
    
    public static Type placeHolder(Loc loc) {
        Type type = new Type();
        type.loc = loc;
        return type;
    }
}
