package com.baidu.titan.dexlib.dx.dex.code;

import com.baidu.titan.dexlib.dx.rop.code.RegisterSpecList;
import com.baidu.titan.dexlib.dx.util.AnnotatedOutput;

/**
 * @author zhangdi07@baidu.com
 * @since 2017/3/5
 */
public class DalvInsnWrapper extends DalvInsn {
    private DalvInsn mOrgDalInsn;

    public DalvInsnWrapper(DalvInsn org) {
        super(org.getOpcode(), org.getPosition(), org.getRegisters());
        this.mOrgDalInsn = org;
    }

    @Override
    public int codeSize() {
        return mOrgDalInsn.codeSize();
    }

    @Override
    public void writeTo(AnnotatedOutput out) {
        mOrgDalInsn.writeTo(out);
    }

    @Override
    public DalvInsn withOpcode(Dop opcode) {
        return mOrgDalInsn.withOpcode(opcode);
    }

    @Override
    public DalvInsn withRegisterOffset(int delta) {
        return mOrgDalInsn.withRegisterOffset(delta);
    }

    @Override
    public DalvInsn withRegisters(RegisterSpecList registers) {
        return mOrgDalInsn.withRegisters(registers);
    }

    @Override
    protected String argString() {
        return mOrgDalInsn.argString();
    }

    @Override
    protected String listingString0(boolean noteIndices) {
        return mOrgDalInsn.listingString0(noteIndices);
    }
}
