//
// Copyright (c) 2024, chunquedong
// Licensed under the Academic Free License version 3.0
//
package sric.compiler.resolve;

import java.util.ArrayDeque;
import sric.compiler.ast.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import sric.compiler.CompilePass;
import sric.compiler.CompilerLog;
import sric.compiler.ast.AstNode;
import sric.compiler.ast.AstNode.*;
import sric.compiler.ast.Expr.*;
import sric.compiler.ast.Stmt.LocalDefStmt;
import sric.compiler.ast.Token.TokenKind;
import static sric.compiler.ast.Token.TokenKind.*;
import sric.compiler.ast.*;
import sric.compiler.ast.Type.MetaTypeInfo;
/**
 *
 * @author yangjiandong
 */
public class ExprTypeResolver extends TypeResolver {
    
    //for func param or for init
    private Scope preScope = null;
    
    private ArrayDeque<AstNode> funcs = new ArrayDeque<AstNode>();
    private ArrayDeque<AstNode> loops = new ArrayDeque<AstNode>();
    
    protected StructDef curStruct = null;
    protected WithBlockExpr curItBlock = null;
    protected Scope curItBlockScope = null;
    
    public ExprTypeResolver(CompilerLog log, SModule module) {
        super(log, module);
        this.log = log;
    }
    
    public void run() {
        module.walkChildren(this);
    }
    
    private Scope lastScope() {
        if (preScope != null) {
            return preScope;
        }
        return scopes.get(scopes.size()-1);
    }
    
    @Override
    protected void resolveId(Expr.IdExpr idExpr) {
        if (idExpr.namespace == null) {
            if (idExpr.name.equals(TokenKind.dot.symbol)) {
                AstNode func = this.funcs.peek();
                if (func instanceof FuncDef f) {
                    if (curItBlock == null) {
                        err("Invalid '.' call", idExpr.loc);
                        return;
                    }
                    //Type self = new Type(curItBlock.loc, curItBlock._structDef.name);
                    //self.id.resolvedDef = curItBlock._structDef;

                    idExpr.resolvedType = curItBlock.resolvedType;
                    return;
                }
                else {
                    err("Use '.' out of struct", idExpr.loc);
                }
                return;
            }
            else if (idExpr.name.equals(TokenKind.thisKeyword.symbol) || 
                    idExpr.name.equals(TokenKind.superKeyword.symbol) ) {
                
                AstNode func = this.funcs.peek();
                if (func instanceof FuncDef f) {
//                    if ((f.flags & FConst.Static) != 0) {
//                        err("No this in static", idExpr.loc);
//                    }
                    if (idExpr.name.equals(TokenKind.superKeyword.symbol)) {
                        if (curStruct == null) {
                            err("Use super out of struct", idExpr.loc);
                            return;
                        }
                        if (curStruct.inheritances == null) {
                            err("Invalid super", idExpr.loc);
                            return;
                        }
                        else {
                            idExpr.resolvedType = Type.pointerType(idExpr.loc, curStruct.inheritances.get(0), Type.PointerAttr.raw, false);
                            idExpr.resolvedType.isImmutable = (f.flags & FConst.Mutable) == 0;
                        }
                    }
                    else if (idExpr.name.equals(TokenKind.thisKeyword.symbol)) {
                        if (curStruct == null) {
                            err("Use this out of struct", idExpr.loc);
                            return;
                        }
                        Type self = new Type(curStruct.loc, curStruct.name);
                        self.id.resolvedDef = curStruct;
                        idExpr.resolvedType = Type.pointerType(idExpr.loc, self, Type.PointerAttr.raw, false);
                        idExpr.resolvedType.isImmutable = (f.flags & FConst.Mutable) == 0;
                    }
                }
                else {
                    err("Use this/super out of struct", idExpr.loc);
                }
                
                return;
            }
        }
        super.resolveId(idExpr);
    }

    @Override
    public void visitUnit(FileUnit v) {
        scopes.add(v.importScope);
        scopes.add(module.getScope());
        this.scopes.add(Buildin.getBuildinScope());
        
        v.walkChildren(this);
        
        popScope();
        popScope();
        popScope();
    }

    @Override
    public void visitField(FieldDef v) {
        
        if (v.initExpr != null) {
            if (v.initExpr instanceof Expr.WithBlockExpr initBlockExpr) {
                initBlockExpr._storeVar = v;
            }
            if (v.initExpr instanceof Expr.ArrayBlockExpr initBlockExpr) {
                initBlockExpr._storeVar = v;
            }
        }
        
        if (v.fieldType != null) {
            resolveType(v.fieldType, false);
        }
        if (v.initExpr != null) {
            this.visit(v.initExpr);
        }

        if (v.fieldType == null) {
            if (v.initExpr == null) {
                err("Miss var type", v.loc);
            }
            else {
                //Type inference
                v.fieldType = v.initExpr.resolvedType;
            }
        }
        
        if (v.isLocalVar) {
            lastScope().put(v.name, v);
        }
        
    }
    
    private void visitFuncPrototype(AstNode.FuncPrototype prototype, Scope scope) {
        if (prototype != null && prototype.paramDefs != null) {
            for (AstNode.ParamDef p : prototype.paramDefs) {
                this.resolveType(p.paramType, false);
                if (p.defualtValue != null) {
                    this.visit(p.defualtValue);
                }
                scope.put(p.name, p);
            }
        }
        
        if (prototype != null) {
            if (prototype.returnType != null && prototype.returnType.isVoid()) {
                this.resolveType(prototype.returnType, false);
            }
        }
    }

    @Override
    public void visitFunc(FuncDef v) {
        this.funcs.push(v);
        
        if (v.generiParamDefs != null) {
            Scope scope = this.pushScope();
            for (GenericParamDef gp : v.generiParamDefs) {
                scope.put(gp.name, gp);
            }
        }
        
        preScope = new Scope();

        visitFuncPrototype(v.prototype, preScope);
        if (v.code != null) {
            this.visit(v.code);
        }
        preScope = null;
        
        if (v.generiParamDefs != null) {
            this.popScope();
        }
        
        funcs.pop();
    }

    @Override
    public void visitTypeDef(TypeDef v) {
        int scopeCount = 1;
        if (v instanceof StructDef sd) {
            curStruct = sd;
            if (sd.inheritances != null) {
                Scope inhScopes = sd.getInheriteScope();
                this.scopes.add(inhScopes);
                ++scopeCount;
                
                for (FieldDef f : sd.fieldDefs) {
                    if (inhScopes.contains(f.name)) {
                        err("Field name is already exsits"+f.name, f.loc);
                    }
                }
                
                for (FuncDef f : sd.funcDefs) {
                    if (/*(f.flags & FConst.Static) != 0 || */(f.flags | FConst.Override) != 0) {
                        continue;
                    }
                    if (inhScopes.contains(f.name)) {
                        err("Func name is already exsits"+f.name, f.loc);
                    }
                }
            }
        }
        else if (v instanceof EnumDef e) {
            int enumValue = 0;
            for (FieldDef f : e.enumDefs) {
                if (f.initExpr != null) {
                    boolean ok = false;
                    if (f.initExpr instanceof Expr.LiteralExpr li) {
                        if (li.value instanceof Long liv) {
                            enumValue = liv.intValue();
                            ok = true;
                        }
                    }
                    if (!ok) {
                        err("Enum value must int literal", v.loc);
                    }
                }
                f._enumValue = enumValue;
                ++enumValue;
            }
        }
        Scope scope = v.getScope();
        this.scopes.add(scope);
        v.walkChildren(this);
        
        for (int i=0; i<scopeCount; ++i) {
            popScope();
        }
        curStruct = null;
    }

    @Override
    public void visitStmt(Stmt v) {
        if (v instanceof Block bs) {
            if (preScope != null) {
                this.scopes.add(preScope);
                preScope = null;
            }
            else {
                pushScope();
            }
            bs.walkChildren(this);
            popScope();
        }
        else if (v instanceof Stmt.IfStmt ifs) {
            this.visit(ifs.condition);
            this.visit(ifs.block);
            if (ifs.elseBlock != null) {
                this.visit(ifs.elseBlock);
            }
        }
        else if (v instanceof Stmt.LocalDefStmt e) {
            this.visit(e.fieldDef);
        }
        else if (v instanceof Stmt.WhileStmt whiles) {
            this.loops.push(v);
            this.visit(whiles.condition);
            this.visit(whiles.block);
            this.loops.pop();
        }
        else if (v instanceof Stmt.ForStmt fors) {
            this.loops.push(v);
            if (fors.init != null) {
                pushScope();
                
                if (fors.init instanceof Stmt.LocalDefStmt varDef) {
                    this.visit(varDef.fieldDef);
                }
                else if (fors.init instanceof Stmt.ExprStmt s) {
                    this.visit(s.expr);
                }
                else {
                    err("Unsupport for init stmt", fors.init.loc);
                }
            }
            
            if (fors.condition != null) {
                this.visit(fors.condition);
            }
            
            if (fors.update != null) {
                this.visit(fors.update);
            }
            this.visit(fors.block);
            
            if (fors.init != null) {
                this.popScope();
            }
            this.loops.pop();
        }
        else if (v instanceof Stmt.SwitchStmt switchs) {
            this.visit(switchs.condition);
            
            for (Stmt.CaseBlock cb : switchs.cases) {
                this.visit(cb.caseExpr);
                this.visit(cb.block);
            }
            
            if (switchs.defaultBlock != null) {
                this.visit(switchs.defaultBlock);
            }
        }
        else if (v instanceof Stmt.ExprStmt exprs) {
            this.visit(exprs.expr);
        }
        else if (v instanceof Stmt.JumpStmt jumps) {
            if (this.loops.size() == 0) {
                err("break, continue outside of loop", v.loc);
            }
        }
        else if (v instanceof Stmt.UnsafeBlock bs) {
            this.visit(bs.block);
        }
        else if (v instanceof Stmt.ReturnStmt rets) {
            if (rets.expr != null) {
                this.visit(rets.expr);
                if (rets.expr.resolvedType != null) {
                    AstNode func = this.funcs.peek();
                    FuncPrototype prototype;
                    if (func instanceof FuncDef f) {
                        prototype = f.prototype;
                    }
                    else {
                        ClosureExpr f = (ClosureExpr)func;
                        prototype = f.prototype;
                    }
                    if (!rets.expr.resolvedType.fit(prototype.returnType)) {
                        err("Return type not fit function", rets.expr.loc);
                    }
                }
            }
        }
        else {
            err("Unkown stmt:"+v, v.loc);
        }
    }
    
    private Type getSlotType(AstNode resolvedDef) {
        if (resolvedDef instanceof FieldDef f) {
            return f.fieldType;
        }
        else if (resolvedDef instanceof FuncDef f) {
            return Type.funcType(f);
        }
        else if (resolvedDef instanceof TypeAlias f) {
            return Type.metaType(f.loc, f.type);
        }
        else if (resolvedDef instanceof TypeDef f) {
            Type type = new Type(f.loc, f.name);
            type.id.resolvedDef = f;
            return Type.metaType(f.loc, type);
        }
        else if (resolvedDef instanceof ParamDef p) {
            return p.paramType;
        }
        return null;
    }
    
    private AstNode resoveOnTarget(Expr target, String name, Loc loc, boolean autoDeref) {
        if (!target.isResolved()) {
            return null;
        }
        AstNode resolvedDef = target.resolvedType.id.resolvedDef;
        if (target.resolvedType.isPointerType() && autoDeref) {
            if (target.resolvedType.genericArgs == null || target.resolvedType.genericArgs.size() > 0) {
                Type type = target.resolvedType.genericArgs.get(0);
                resolvedDef = type.id.resolvedDef;
            }
            else {
                resolvedDef = null;
            }
        }
        else if (target.resolvedType.isRefableType()) {
            if (target.resolvedType.genericArgs == null || target.resolvedType.genericArgs.size() > 0) {
                Type type = target.resolvedType.genericArgs.get(0);
                resolvedDef = type.id.resolvedDef;
            }
            else {
                resolvedDef = null;
            }
        }
        
        if (resolvedDef == null) {
            return null;
        }

        if (resolvedDef instanceof TypeDef t) {
            Scope scope = t.getScope();
            AstNode def = scope.get(name, loc, log);
            if (def == null) {
                if (t instanceof StructDef sd) {
                    if (sd.inheritances != null) {
                        Scope inhScopes = sd.getInheriteScope();
                        def = inhScopes.get(name, loc, log);
                    }
                }
            }
            if (def == null) {
                err("Unkown name:"+name, loc);
            }
            return def;
        }

        return null;
    }

    @Override
    public void visitExpr(Expr v) {
        if (v instanceof Expr.IdExpr e) {
            resolveId(e);
            if (e.resolvedDef != null) {
                e.resolvedType = getSlotType(e.resolvedDef);
            }
        }
        else if (v instanceof Expr.AccessExpr e) {
            this.visit(e.target);
            e.resolvedDef = resoveOnTarget(e.target, e.name, e.loc, true);
            if (e.resolvedDef != null) {
                e.resolvedType = getSlotType(e.resolvedDef);
            }
            else {
                if (e.target.resolvedType != null && e.target.resolvedType.isMetaType()) {
                    err("Can't call method on Type", e.loc);
                }
                else {
                    err("Unknow access:"+e.name, e.loc);
                }
            }
        }
        else if (v instanceof Expr.LiteralExpr e) {
            if (e.value == null) {
                v.resolvedType = Type.nullType(e.loc);
            }
            else if (e.value instanceof Long) {
                v.resolvedType = Type.intType(e.loc);
            }
            else if (e.value instanceof Double) {
                v.resolvedType = Type.floatType(e.loc);
            }
            else if (e.value instanceof Boolean) {
                v.resolvedType = Type.boolType(e.loc);
            }
            else if (e.value instanceof String) {
                v.resolvedType = Type.strType(e.loc);
            }
        }
        else if (v instanceof Expr.BinaryExpr e) {
            resolveBinaryExpr(e);
        }
        else if (v instanceof Expr.CallExpr e) {
            resolveCallExpr(e);
        }
        else if (v instanceof Expr.UnaryExpr e) {
            this.visit(e.operand);
            if (e.operand.isResolved()) {
                TokenKind curt = e.opToken;
                switch (curt) {
                    //~
                    case tilde:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //!
                    case bang:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //+, -
                    case plus:
                    case minus:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //*
                    case star:
                        if (!e.operand.resolvedType.isPointerType()) {
                            err("Invalid * for non pointer", e.loc);
                        }
                        else {
                            e.resolvedType = e.operand.resolvedType.genericArgs.get(0);
                        }
                        break;
                    //++, --
                    case increment:
                    case decrement:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //&
                    case amp:
                        if (e.operand instanceof Expr.LiteralExpr lexpr) {
                            err("Invalid & for literal", e.loc);
                        }
                        Type elmentType = e.operand.resolvedType;
                        if (e.operand.resolvedType.isArray()) {
                            elmentType = e.operand.resolvedType.genericArgs.get(0);
                        }
                        
                        if (e.operand.resolvedType.isRefableType()) {
                            e.resolvedType = Type.pointerType(e.loc, elmentType, Type.PointerAttr.ref, false);
                        }
                        else {
                            e.resolvedType = Type.pointerType(e.loc, elmentType, Type.PointerAttr.raw, false);
                        }
                        break;
                    case awaitKeyword:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    case moveKeyword:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    default:
                        break;
                }
            }
        }
        else if (v instanceof Expr.TypeExpr e) {
            this.resolveType(e.type, true);
            e.resolvedType = Type.metaType(e.loc, e.type);
        }
        else if (v instanceof Expr.IndexExpr e) {
            this.visit(e.target);
            this.visit(e.index);
            
            if (e.target.isResolved()) {
                if (e.target.resolvedType.isArray() && e.target.resolvedType.genericArgs != null) {
                    e.resolvedType = e.target.resolvedType.genericArgs.get(0);
                }
                else if (e.target.resolvedType.isRawPointerType()) {
                    if (e.target.resolvedType.genericArgs != null) {
                        e.resolvedType = e.target.resolvedType.genericArgs.get(0);
                    }
                    else {
                        err("Unknow operator []", e.loc);
                    }
                }
                else {
                    String operatorName = e.inLeftSide ? Buildin.setOperator : Buildin.getOperator;
                    AstNode rdef = resoveOnTarget(e.target, operatorName, e.loc, false);
                    if (rdef == null) {
                        err("Unknow operator []", e.loc);
                    }
                    else if (rdef instanceof FuncDef f) {
                        if ((f.flags & FConst.Operator) == 0) {
                            err("Expected operator", e.loc);
                        }
                        e.resolvedOperator = f;
                        e.resolvedType = f.prototype.returnType;
                    }
                    else {
                        err("Invalid operator []", e.loc);
                    }
                }
            }
        }
        else if (v instanceof Expr.GenericInstance e) {

            resolveGenericInstance(e);
        }
        else if (v instanceof Expr.IfExpr e) {
            this.visit(e.condition);
            this.visit(e.trueExpr);
            this.visit(e.falseExpr);
            e.resolvedType = e.trueExpr.resolvedType;
        }
        else if (v instanceof Expr.WithBlockExpr e) {
            resolveWithBlockExpr(e);
        }
        else if (v instanceof Expr.ArrayBlockExpr e) {
            resolveArrayBlockExpr(e);
        }
        else if (v instanceof ClosureExpr e) {
            this.funcs.push(v);

//            for (Expr t : e.captures) {
//                this.visit(t);
//            }
            
            preScope = new Scope();
            
            visitFuncPrototype(e.prototype, preScope);
            this.visit(e.code);
            
            preScope = null;
            this.funcs.pop();
            
            e.resolvedType = Type.funcType(e);
        }
        else if (v instanceof NonNullableExpr e) {
            this.visit(e.operand);
            boolean ok = false;
            if (e.operand.resolvedType != null) {
                if (e.operand.resolvedType.detail instanceof Type.PointerInfo pt) {
                    if (pt.isNullable) {
                        e.resolvedType = e.operand.resolvedType.toNonNullable();
                        ok = true;
                    }
                }
            }
            if (!ok) {
                err("Invalid non-nullable", e.operand.loc);
            }
        }
        else {
            err("Unkown expr:"+v, v.loc);
            return;
        }
        
        if (v.resolvedType == null) {
            err("Resolved fail", v.loc);
        }
    }
    
    private StructDef getTypeStructDef(Type type) {
        if (type.isPointerType() && type.genericArgs != null) {
            type = type.genericArgs.get(0);
        }
        if (type.id.resolvedDef instanceof StructDef sd) {
            return sd;
        }
        return null;
    }

    private void resolveWithBlockExpr(Expr.WithBlockExpr e) {
        this.visit(e.target);
        if (!e.target.isResolved()) {
            return;
        }
        
        StructDef sd = null;
        if (e.target instanceof IdExpr id) {
            if (id.resolvedDef instanceof StructDef) {
                sd = (StructDef)id.resolvedDef;
                //e._isType = true;
            }
            else if (id.resolvedDef instanceof FieldDef fd) {
                if (fd.fieldType.id.resolvedDef instanceof StructDef fieldSF) {
                    sd = fieldSF;
                }
            }
            else {
                sd = getTypeStructDef(e.target.resolvedType);
            }
        }
        else if (e.target instanceof GenericInstance gi) {
            if (gi.resolvedDef instanceof StructDef) {
                sd = (StructDef)gi.resolvedDef;
                //e._isType = true;
            }
        }
        else if (e.target instanceof CallExpr call) {
            sd = getTypeStructDef(e.target.resolvedType);
        }
        else if (e.target instanceof TypeExpr te) {
            if (te.type.id.resolvedDef instanceof StructDef) {
                sd = (StructDef)te.type.id.resolvedDef;
                //e._isType = true;
            }
            //e._isType = true;
        }
        
        e._structDef = sd;
        e._isType = e.target.resolvedType.isMetaType();

        if (sd != null) {
            if (e.target.resolvedType.detail instanceof MetaTypeInfo mt) {
                e.resolvedType = mt.type;
            }
            else {
                e.resolvedType = e.target.resolvedType;
            }
        }
        
        if (e.block != null && sd != null) {
            WithBlockExpr savedCurItBlock = curItBlock;
            curItBlock = e;
            
            //avoid jump out from with block
            ArrayDeque<AstNode> savedLoop = this.loops;
            this.loops = new ArrayDeque<AstNode>();
            
            this.visit(e.block);

            curItBlock = savedCurItBlock;
            this.loops = savedLoop;
        }
    }
    
    private void resolveArrayBlockExpr(Expr.ArrayBlockExpr e) {
        this.resolveType(e.type, true);
        if (!e.type.id.isResolved()) {
            return;
        }
        
        if (e.type.detail instanceof Type.ArrayInfo at) {
            at.sizeExpr = new LiteralExpr(Long.valueOf(e.args.size()));
            at.sizeExpr.loc = e.loc;
            //at.size = e.args.size();

            e.resolvedType = e.type;
        }
        else {
            err("Invalid array", e.loc);
            return;
        }
        
        if (e.args != null) {
            for (Expr t : e.args) {
                this.visit(t);
            }
        }
    }
    
    private void resolveGenericInstance(Expr.GenericInstance e) {
        this.visit(e.target);
        for (Type t : e.genericArgs) {
            this.resolveType(t, false);
        }
        if (!e.target.isResolved()) {
            return;
        }
        
        IdExpr idExpr;
        if (e.target instanceof IdExpr) {
            idExpr = (IdExpr)e.target;
        }
        else {
            err("Unexpected generic args", e.loc);
            return;
        }
        
        if (e.genericArgs != null) {
            boolean genericOk = false;
            if (idExpr.resolvedDef instanceof StructDef sd) {
                if (sd.generiParamDefs != null) {
                    if (e.genericArgs.size() == sd.generiParamDefs.size()) {
                        e.resolvedDef = sd.parameterize(e.genericArgs);
                        e.resolvedType = getSlotType(e.resolvedDef);
                        genericOk = true;
                    }
                }
            }
            else if (idExpr.resolvedDef instanceof FuncDef sd) {
                if (sd.generiParamDefs != null) {
                    if (e.genericArgs.size() == sd.generiParamDefs.size()) {
                        e.resolvedDef = sd.parameterize(e.genericArgs);
                        e.resolvedType = getSlotType(e.resolvedDef);
                        genericOk = true;
                    }
                }
            }
            if (!genericOk) {
                err("Generic args size not match", e.loc);
            }
        }
        else if (idExpr.resolvedDef instanceof StructDef sd) {
            if (sd.generiParamDefs != null) {
                err("Miss generic args", idExpr.loc);
            }
        }
        else if (idExpr.resolvedDef instanceof FuncDef sd) {
            if (sd.generiParamDefs != null) {
                err("Miss generic args", idExpr.loc);
            }
        }
    }

    private void resolveCallExpr(Expr.CallExpr e) {
        this.visit(e.target);
        if (e.args != null) {
            for (Expr.CallArg t : e.args) {
                this.visit(t.argExpr);
            }
        }
        
        if (e.target.isResolved()) {
            if (e.target.resolvedType.detail instanceof Type.FuncInfo f) {
                e.resolvedType = f.prototype.returnType;
            }
            else {
                err("Invalid call target", e.loc);
            }
        }
    }
    
    private void resolveBinaryExpr(Expr.BinaryExpr e) {

        this.visit(e.lhs);
        this.visit(e.rhs);
        
        if (e.lhs.isResolved() && e.rhs.isResolved()) {
            TokenKind curt = e.opToken;
            switch (curt) {
                case isKeyword:
                    e.resolvedType = Type.boolType(e.loc);
                    break;
                case asKeyword:
                    if (e.rhs instanceof TypeExpr te) {
                        Type from = e.lhs.resolvedType;
                        Type to = te.type;
                        if (from.detail instanceof Type.PointerInfo p1 && to.detail instanceof Type.PointerInfo p2) {
                            if (p1.pointerAttr != Type.PointerAttr.raw && p2.pointerAttr == Type.PointerAttr.raw) {
                                e.lhs.implicitTypeConvertTo = Type.pointerType(e.lhs.loc, from.genericArgs.get(0), p2.pointerAttr, p2.isNullable);
                                e.lhs.isPointerConvert = true;
                            }
                            else if (p1.pointerAttr != Type.PointerAttr.ref && p2.pointerAttr == Type.PointerAttr.ref) {
                                e.lhs.implicitTypeConvertTo = Type.pointerType(e.lhs.loc, from.genericArgs.get(0), p2.pointerAttr, p2.isNullable);
                                e.lhs.isPointerConvert = true;
                            }
                            else if (p1.pointerAttr != p2.pointerAttr) {
                                err("Unknow convert", e.loc);
                            }
                        }
                        e.resolvedType = to;
                    }
                    break;
                case eq:
                case notEq:
//                case same:
//                case notSame:
                case lt:
                case gt:
                case ltEq:
                case gtEq:
                    if (e.lhs.resolvedType.isNum() && e.rhs.resolvedType.isNum()) {
                        //OK
                    }
                    else if (e.lhs.resolvedType.isPointerType()&& e.rhs.resolvedType.isPointerType()) {
                        //OK
                    }
                    else {
                        String operatorName = Buildin.operatorToName(TokenKind.cmp);
                        AstNode rdef = resoveOnTarget(e.lhs, operatorName, e.loc, false);
                        if (rdef == null) {
                            err("Unknow operator:"+curt, e.loc);
                        }
                        else if (rdef instanceof FuncDef f) {
                            e.resolvedOperator = f;
                        }
                        else {
                            err("Invalid operator:"+curt, e.loc);
                        }
                    }
                    e.resolvedType = Type.boolType(e.loc);
                    break;
                case doubleAmp:
                case doublePipe:
                    e.resolvedType = Type.boolType(e.loc);
                    break;
                case leftShift:
                case rightShift:
                case pipe:
                case caret:
                case amp:
                case percent:
                    e.resolvedType = Type.intType(e.loc);
                    break;
                case plus:
                case minus:
                case star:
                case slash:
                    Type lt = e.lhs.resolvedType;
                    Type rt = e.rhs.resolvedType;
                    if (lt.isRefableType()) {
                        lt = lt.genericArgs.get(0);
                    }
                    if (rt.isRefableType()) {
                        rt = rt.genericArgs.get(0);
                    }
                    if (lt.isInt() && rt.isInt()) {
                        e.resolvedType = lt;
                    }
                    else if (lt.isFloat() && rt.isFloat()) {
                        e.resolvedType = lt;
                    }
                    else if ((lt.isFloat() && rt.isInt()) ||
                            (lt.isInt() && rt.isFloat())) {
                        e.resolvedType = Type.floatType(e.loc);
                    }
                    //pointer arithmetic: +,-
                    else if ((curt == plus || curt == minus) && lt.isRawPointerType() && rt.isInt()) {
                        e.resolvedType = lt;
                    }
                    else {
                        resolveMathOperator(curt, e);
                    }
                    break;
                case assign:
                case assignPlus:
                case assignMinus:
                case assignStar:
                case assignSlash:
                case assignPercent:
                    if (e.lhs.resolvedType.isNum() && e.rhs.resolvedType.isNum()) {
                        //ok
                    }
                    else {
                        if (curt != TokenKind.assign) {
                            err("Unsupport operator:"+curt, e.loc);
                        }
                    }
                    e.resolvedType = e.lhs.resolvedType;

                    break;
                default:
                    break;
            }
        }
    }

    private void resolveMathOperator(TokenKind curt, Expr.BinaryExpr e) {
        String operatorName = Buildin.operatorToName(curt);
        if (operatorName == null) {
            err("Unknow operator:"+curt, e.loc);
        }
        AstNode rdef = resoveOnTarget(e.lhs, operatorName, e.loc, false);
        if (rdef == null) {
            err("Unknow operator:"+curt, e.loc);
        }
        else if (rdef instanceof FuncDef f) {
            if ((f.flags & FConst.Operator) == 0) {
                err("Expected operator", e.loc);
            }
            e.resolvedType = f.prototype.returnType;
            e.resolvedOperator = f;
        }
        else {
            err("Invalid operator:"+curt, e.loc);
        }
    }

}
