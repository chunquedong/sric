//
// Copyright (c) 2024, chunquedong
// Licensed under the Academic Free License version 3.0
//
package sric.compiler.ast;

import java.util.ArrayList;
import java.util.HashMap;
import sric.compiler.ast.AstNode.*;
import sric.compiler.ast.Token.TokenKind;

/**
 *
 * @author yangjiandong
 */
public class Buildin {
        
    private static Scope buildinScope;
    
    public static final Loc loc = new Loc("buildin", 0, 0, 0);
    
    public static final String funcTypeName = "=>";
    public static final String arrayTypeName = "[]";
    public static final String pointerTypeName = "*";
    public static final String varargTypeName = "...";
    public static final String metaTypeTypeName = "_Type";
    public static final String defaultGenericParamTypeName = "_GenericParam"; //for generic param

    private static TypeDef makeBuildinType(Scope scope, String name) {
        return makeBuildinType(scope, name, null);
    }
    private static TypeDef makeBuildinType(Scope scope, String name, ArrayList<GenericParamDef> gps) {
        TypeDef typeDef = new AstNode.TypeDef(null, FConst.Unsafe, name);
        typeDef.loc = loc;
        typeDef.generiParamDefs = gps;
        scope.put(name, typeDef);
        return typeDef;
    }
    
//    private static FuncDef isNullFunc(Scope scope) {
//        FuncDef f = new FuncDef();
//        f.loc = loc;
//        f.name = "isNull";
//        f.prototype.returnType = Type.boolType(loc);
//        f.prototype.paramDefs = new ArrayList<FieldDef>();
//        FieldDef param = new FieldDef("pointer", Type.pointerType(loc, Type.voidType(loc), Type.PointerAttr.raw, true));
//        param.loc = loc;
//        f.prototype.paramDefs.add(param);
//        
//        scope.put(f.name, f);
//        return f;
//    }
    
    private static FuncDef sizeofFunc(Scope scope) {
        FuncDef f = new FuncDef();
        f.loc = loc;
        f.name = "sizeof";
        f.prototype.returnType = Type.intType(loc);
        f.prototype.paramDefs = new ArrayList<FieldDef>();
        FieldDef param = new FieldDef("type", Type.metaType(loc, Type.voidType(loc)));
        param.loc = loc;
        param.name = "type";
        f.prototype.paramDefs.add(param);
        
        scope.put(f.name, f);
        return f;
    }
    
    private static FuncDef offsetofFunc(Scope scope) {
        FuncDef f = new FuncDef();
        f.loc = loc;
        f.name = "offsetof";
        f.prototype.returnType = Type.intType(loc);
        f.prototype.paramDefs = new ArrayList<FieldDef>();
        FieldDef param = new FieldDef("type",  Type.metaType(loc, Type.voidType(loc)));
        param.loc = loc;
        f.prototype.paramDefs.add(param);
        
        FieldDef param2 = new FieldDef("field", Type.metaType(loc, Type.voidType(loc)));
        param2.loc = loc;
        f.prototype.paramDefs.add(param2);
        
        scope.put(f.name, f);
        return f;
    }
    
    public static Scope getBuildinScope() {
        if (buildinScope == null) {
            Scope scope = new Scope();

            makeBuildinType(scope, "Int");
            makeBuildinType(scope, "Bool");
            makeBuildinType(scope, "Float");
            
            ArrayList<GenericParamDef> gps = new ArrayList<GenericParamDef>();
            GenericParamDef gp = new GenericParamDef();
            gp.name = "T";
            gp.loc = loc;
            gps.add(gp);
            makeBuildinType(scope, arrayTypeName, gps);//array
            
            ArrayList<GenericParamDef> gps2 = new ArrayList<GenericParamDef>();
            GenericParamDef gp2 = new GenericParamDef();
            gp2.name = "T";
            gp2.loc = loc;
            gps2.add(gp2);
            makeBuildinType(scope, pointerTypeName, gps2);//pointer
            
            makeBuildinType(scope, "Void");
            makeBuildinType(scope, varargTypeName);//varargs
            makeBuildinType(scope, funcTypeName);//func
            makeBuildinType(scope, defaultGenericParamTypeName);//.flags = FConst.Noncopyable;

            buildinScope = scope;
            
            sizeofFunc(scope);
            offsetofFunc(scope);
            //isNullFunc(scope);
        }
        return buildinScope;
    }
    
    public static final String getOperator = "get";
    public static final String setOperator = "set";
    
    public static String operatorToName(TokenKind tok) {
        switch (tok) {
            case plus:
                return "plus";
            case minus:
                return "minus";
            case star:
                return "mult";
            case slash:
                return "div";
            case cmp:
                return "compare";
        }
        return null;
    }
}
