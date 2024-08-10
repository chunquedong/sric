//
// Copyright (c) 2024, chunquedong
// Licensed under the Academic Free License version 3.0
//
package sc2.compiler.resolve;

import java.util.ArrayDeque;
import sc2.compiler.ast.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import sc2.compiler.CompilePass;
import sc2.compiler.CompilerLog;
import sc2.compiler.ast.AstNode;
import sc2.compiler.ast.AstNode.*;
import sc2.compiler.ast.*;
import sc2.compiler.ast.Expr.AccessExpr;
import sc2.compiler.ast.Expr.IdExpr;
import sc2.compiler.ast.Stmt.LocalDefStmt;
import sc2.compiler.ast.Token.TokenKind;
import static sc2.compiler.ast.Token.TokenKind.*;
import sc2.compiler.ast.Type.FuncType;

/**
 *
 * @author yangjiandong
 */
public class ExprTypeResolver extends CompilePass {
    
    private ArrayList<Scope> scopes = new ArrayList<>();
    private SModule module;
    
    //for func param or for init
    private Scope preScope = null;
    
    private ArrayDeque<AstNode> funcs = new ArrayDeque<AstNode>();
    private ArrayDeque<AstNode> loops = new ArrayDeque<AstNode>();
    
    public ExprTypeResolver(CompilerLog log, SModule module) {
        super(log);
        this.module = module;
        this.log = log;
    }
    
    public void run() {
        module.walkChildren(this);
    }
    
    private Scope pushScope() {
        Scope s = new Scope();
        scopes.add(s);
        return s;
    }
    
    private Scope popScope() {
        return scopes.remove(scopes.size()-1);
    }
    
    private Scope lastScope() {
        if (preScope != null) {
            return preScope;
        }
        return scopes.get(scopes.size()-1);
    }
    
    private AstNode findSymbol(String name, Loc loc) {
        for (int i = scopes.size()-1; i >=0; --i) {
            Scope scope = scopes.get(i);
            AstNode node = scope.get(name, loc, log);
            if (node != null) {
                return node;
            }
        }
        err("Unknow symbol "+name, loc);
        return null;
    }
    
    private void resolveId(Expr.IdExpr idExpr) {
        if (idExpr.namespace == null) {
            idExpr.resolvedDef = findSymbol(idExpr.name, idExpr.loc);
            return;
        }
        resolveId(idExpr.namespace);
        if (idExpr.namespace.resolvedDef == null) {
            return;
        }
        if (idExpr.namespace.resolvedDef instanceof SModule m) {
            AstNode node = m.getScope().get(idExpr.name, idExpr.loc, log);
            if (node == null) {
                err("Unknow symbol "+idExpr.name, idExpr.loc);
            }
            idExpr.resolvedDef = node;
            return;
        }
        else if (idExpr.namespace.resolvedDef instanceof TypeDef m) {
            AstNode node = m.getScope().get(idExpr.name, idExpr.loc, log);
            if (node == null) {
                err("Unknow symbol "+idExpr.name, idExpr.loc);
            }
            idExpr.resolvedDef = node;
            return;
        }
        else {
            err("Unsupport :: for "+idExpr.namespace.name, idExpr.loc);
        }
    }
        
    private void resolveType(Type type) {
        resolveId(type.id);
        if (type.id.resolvedDef != null) {
            if (!(type.id.resolvedDef instanceof TypeDef)) {
                type.id.resolvedDef = null;
                err("It's not a type "+type.id.name, type.loc);
            }
            return;
        }
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
        if (v.fieldType != null) {
            resolveType(v.fieldType);
        }
        if (v.initExpr != null) {
            this.visit(v.initExpr);
            if (v.initExpr.resolvedType != null) {
                if (v.fieldType == null) {
                    v.fieldType = v.initExpr.resolvedType;
                }
                else {
                    if (!v.initExpr.resolvedType.fit(v.fieldType)) {
                        err("Invalid assign", v.loc);
                    }
                }
            }
        }
        if (v.isLocalVar) {
            lastScope().put(v.name, v);
        }
    }
    
    private void visitFuncPrototype(AstNode.FuncPrototype prototype, Scope scope) {
        if (prototype != null && prototype.paramDefs != null) {
            for (AstNode.ParamDef p : prototype.paramDefs) {
                this.resolveType(p.paramType);
                if (p.defualtValue != null) {
                    this.visit(p.defualtValue);
                }
                scope.put(p.name, p);
            }
        }
        
        if (prototype != null) {
            if (prototype.returnType != null && prototype.returnType.isVoid()) {
                this.resolveType(prototype.returnType);
            }
        }
    }

    @Override
    public void visitFunc(FuncDef v) {
        this.funcs.push(v);
        preScope = new Scope();
        visitFuncPrototype(v.prototype, preScope);
        if (v.code != null) {
            this.visit(v.code);
        }
        preScope = null;
        
        funcs.pop();
    }

    @Override
    public void visitTypeDef(TypeDef v) {
        Scope scope = v.getScope();
        this.scopes.add(scope);
        v.walkChildren(this);
        popScope();
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
            verifyBool(ifs.condition);
        }
        else if (v instanceof Stmt.LocalDefStmt e) {
            this.visit(e.fieldDef);
        }
        else if (v instanceof Stmt.WhileStmt whiles) {
            this.loops.push(v);
            this.visit(whiles.condition);
            this.visit(whiles.block);
            verifyBool(whiles.condition);
            this.loops.pop();
        }
        else if (v instanceof Stmt.ForStmt fors) {
            this.loops.push(v);
            if (fors.init != null) {
                if (preScope == null) {
                    preScope = new Scope();
                }
                
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
                verifyBool(fors.condition);
            }
            
            if (fors.update != null) {
                this.visit(fors.update);
            }
            this.visit(fors.block);
            this.loops.pop();
        }
        else if (v instanceof Stmt.SwitchStmt switchs) {
            this.visit(switchs.condition);
            verifyInt(switchs.condition);
            
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
                    AstNode func = this.funcs.getLast();
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
            //TODO
            return Type.metaType(f.loc, new Type(f.loc, f.name));
        }
        return null;
    }
    
    private void verifyBool(Expr condition) {
        if (condition.resolvedType != null && !condition.resolvedType.isBool()) {
            err("Must be Bool", condition.loc);
        }
    }
    
    private void verifyInt(Expr e) {
        if (e.resolvedType != null && !e.resolvedType.isInt()) {
            err("Must be Int type", e.loc);
        }
    }
    
    private void verifyMetType(Expr e) {
        if (e.resolvedType != null && !e.resolvedType.isMetaType()) {
            err("Type required", e.loc);
        }
    }
    
    private AstNode resoveOnTarget(Expr target, String name, Loc loc) {
        if (!target.isResolved()) {
            return null;
        }
        
        AstNode resolvedDef = target.resolvedType.id.resolvedDef;
        if (resolvedDef != null) {
            if (resolvedDef instanceof TypeDef t) {
                Scope scope = t.getScope();
                AstNode def = scope.get(name, loc, log);
                if (def == null) {
                    err("Unkown name:"+name, loc);
                }
                return def;
            }
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
            e.resolvedDef = resoveOnTarget(e.target, e.name, e.loc);
            if (e.resolvedDef != null) {
                e.resolvedType = getSlotType(e.resolvedDef);
            }
        }
        else if (v instanceof Expr.LiteralExpr e) {
            if (e.value instanceof Long) {
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
            this.visit(e.target);
            if (e.args != null) {
                for (Expr.CallArg t : e.args) {
                    this.visit(t.argExpr);
                }
            }
            if (e.target.isResolved()) {
                if (e.target.resolvedType instanceof FuncType f) {
                    e.resolvedType = f.prototype.returnType;
                }
                else {
                    err("Call a non-function type:"+e.target, e.loc);
                }
            }
        }
        else if (v instanceof Expr.UnaryExpr e) {
            this.visit(e.operand);
            if (e.operand.isResolved()) {
                TokenKind curt = e.opToken;
                switch (curt) {
                    //~
                    case tilde:
                        verifyInt(e.operand);
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //!
                    case bang:
                        verifyBool(e.operand);
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
                        verifyInt(e.operand);
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    //&
                    case amp:
                        e.resolvedType = Type.pointerType(e.loc, e.operand.resolvedType, Type.PointerAttr.ref, true);
                        break;
                    case awaitKeyword:
                        e.resolvedType = e.operand.resolvedType;
                        break;
                    default:
                        break;
                }
            }
        }
        else if (v instanceof Expr.TypeExpr e) {
            e.resolvedType = Type.metaType(e.loc, e.type);
        }
        else if (v instanceof Expr.IndexExpr e) {
            this.visit(e.target);
            this.visit(e.index);
            verifyInt(e.index);
            
            if (e.target.isResolved()) {
                if (e.target.resolvedType.isArray() && e.target.resolvedType.genericArgs != null) {
                    e.resolvedType = e.target.resolvedType.genericArgs.get(0);
                }
                else {
                    String operatorName = e.inLeftSide ? Buildin.setOperator : Buildin.setOperator;
                    AstNode rdef = resoveOnTarget(e.target, operatorName, e.loc);
                    if (rdef != null) {
                        err("Unknow operator []", e.loc);
                    }
                    else if (rdef instanceof FuncDef f) {
                        int expectedParamSize = e.inLeftSide ? 2:1;
                        if (f.prototype.paramDefs == null || f.prototype.paramDefs.size() != expectedParamSize) {
                            err("Invalid operator []", e.loc);
                        }
                        else if (f.prototype.returnType == null || f.prototype.returnType.isVoid()) {
                            err("Invalid operator []", e.loc);
                        }
                        else if (!(f.prototype.paramDefs.get(0).paramType.isInt())) {
                            err("Invalid arg type for operator []", e.loc);
                        }
                    }
                    else {
                        err("Invalid operator []", e.loc);
                    }
                    err("Unsupport [] for "+e.target.resolvedType, e.loc);
                }
            }
        }
        else if (v instanceof Expr.GenericInstance e) {
            this.visit(e.target);
            for (Type t : e.genericArgs) {
                this.visit(t);
            }
        }
        else if (v instanceof Expr.IfExpr e) {
            this.visit(e.condition);
            this.visit(e.trueExpr);
            this.visit(e.falseExpr);
            verifyBool(e.condition);
        }
        else if (v instanceof Expr.InitBlockExpr e) {
            this.visit(e.target);
            for (Expr.CallArg t : e.args) {
                this.visit(t.argExpr);
            }
            e.resolvedType = e.target.resolvedType;
        }
        else if (v instanceof ClosureExpr e) {
            this.funcs.push(v);

            for (Expr t : e.captures) {
                this.visit(t);
            }
            
            preScope = new Scope();
            
            visitFuncPrototype(e.prototype, preScope);
            this.visit(e.code);
            
            preScope = null;
            this.funcs.pop();
            
            e.resolvedType = Type.voidType(e.loc);
        }
        else {
            err("Unkown expr:"+v, v.loc);
        }
    }

    private void resolveBinaryExpr(Expr.BinaryExpr e) {

        this.visit(e.lhs);
        this.visit(e.rhs);
        
        if (e.lhs.isResolved() && e.rhs.isResolved()) {
            TokenKind curt = e.opToken;
            switch (curt) {
                case isKeyword:
                    verifyMetType(e.rhs);
                    e.resolvedType = Type.boolType(e.loc);
                    break;
                case asKeyword:
                    verifyMetType(e.rhs);
                    e.resolvedType = e.rhs.resolvedType;
                    break;
                case eq:
                case notEq:
                case same:
                case notSame:
                case lt:
                case gt:
                case ltEq:
                case gtEq:
                    if (e.lhs.resolvedType.isInt() && e.rhs.resolvedType.isInt()) {
                        //OK
                    }
                    else if (e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isFloat()) {
                        //OK
                    }
                    else if ((e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isInt()) ||
                            (e.lhs.resolvedType.isInt() && e.rhs.resolvedType.isFloat())) {
                        if (curt == TokenKind.eq || curt == TokenKind.notEq || curt == TokenKind.same || curt == TokenKind.notSame) {
                            err("Cant compare different type", e.loc);
                        }
                    }
                    else if (!e.lhs.resolvedType.equals(e.rhs.resolvedType)) {
                        err("Cant compare different type", e.loc);
                    }
                    else {
                        String operatorName = Buildin.tokenOperator.get(TokenKind.cmp);
                        AstNode rdef = resoveOnTarget(e.lhs, operatorName, e.loc);
                        if (rdef != null) {
                            err("Unknow operator:"+curt, e.loc);
                        }
                        else if (rdef instanceof FuncDef f) {
                            if (!f.prototype.returnType.isBool()) {
                                err("Invalid operator:"+curt, e.loc);
                            }
                            else if (f.prototype.paramDefs == null || f.prototype.paramDefs.size() != 1) {
                                err("Invalid operator:"+curt, e.loc);
                            }
                        }
                        else {
                            err("Invalid operator:"+curt, e.loc);
                        }
                    }
                    e.resolvedType = Type.boolType(e.loc);
                    break;
                case leftShift:
                case rightShift:
                case pipe:
                case caret:
                case amp:
                case percent:
                    verifyInt(e.lhs);
                    verifyInt(e.rhs);
                    e.resolvedType = Type.intType(e.loc);
                    break;
                case plus:
                case minus:
                case star:
                case slash:
                    if (e.lhs.resolvedType.isInt() && e.rhs.resolvedType.isInt()) {
                        e.resolvedType = e.lhs.resolvedType;
                    }
                    else if (e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isFloat()) {
                        e.resolvedType = e.lhs.resolvedType;
                    }
                    else if ((e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isInt()) ||
                            (e.lhs.resolvedType.isInt() && e.rhs.resolvedType.isFloat())) {
                        e.resolvedType = Type.floatType(e.loc);
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
                    boolean ok = false;
                    if (e.lhs instanceof IdExpr idExpr) {
                        if (idExpr.resolvedDef instanceof FieldDef f) {
                            ok = true;
                        }
                    }
                    else if (e.lhs instanceof AccessExpr accessExpr) {
                        if (accessExpr.resolvedDef instanceof FieldDef f) {
                            ok = true;
                        }
                    }
                    
                    if (!ok) {
                        err("Not assignable", e.lhs.loc);
                    }
                    else {
                        if (e.lhs.resolvedType.isInt() && e.rhs.resolvedType.isInt()) {
                            e.resolvedType = e.lhs.resolvedType;
                        }
                        else if (e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isFloat()) {
                            e.resolvedType = e.lhs.resolvedType;
                        }
                        else if (e.lhs.resolvedType.isFloat() && e.rhs.resolvedType.isInt()) {
                            //OK
                        }
                        else if (!e.lhs.resolvedType.equals(e.rhs.resolvedType)) {
                            err("Cant compare different type", e.loc);
                        }
                        else {
                            TokenKind overrideToken = null;
                            if (curt == TokenKind.assignPlus) {
                                overrideToken = TokenKind.plus;
                            }
                            else if (curt == TokenKind.assignMinus) {
                                overrideToken = TokenKind.minus;
                            }
                            else if (curt == TokenKind.assignStar) {
                                overrideToken = TokenKind.star;
                            }
                            else if (curt == TokenKind.assignSlash) {
                                overrideToken = TokenKind.slash;
                            }

                            if (overrideToken != null) {
                                resolveMathOperator(overrideToken, e);
                            }
                            else {
                                err("Unsupport operator:"+curt, e.loc);
                            }
                        }
                        e.resolvedType = e.lhs.resolvedType;
                    }
                    
                    break;
                default:
                    break;
            }
        }
    }

    private void resolveMathOperator(TokenKind curt, Expr.BinaryExpr e) {
        String operatorName = Buildin.tokenOperator.get(curt);
        AstNode rdef = resoveOnTarget(e.lhs, operatorName, e.loc);
        if (rdef != null) {
            err("Unknow operator:"+curt, e.loc);
        }
        else if (rdef instanceof FuncDef f) {
            if (f.prototype.paramDefs == null || f.prototype.paramDefs.size() != 1) {
                err("Invalid operator:"+curt, e.loc);
            }
            else if (f.prototype.returnType == null || f.prototype.returnType.isVoid()) {
                err("Invalid operator:"+curt, e.loc);
            }
            else if (!e.rhs.resolvedType.fit(f.prototype.paramDefs.get(0).paramType)) {
                err("Invalid arg type:"+e.rhs.resolvedType, e.rhs.loc);
            }
        }
        else {
            err("Invalid operator:"+curt, e.loc);
        }
    }

}