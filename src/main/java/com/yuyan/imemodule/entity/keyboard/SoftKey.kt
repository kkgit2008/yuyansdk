package com.yuyan.imemodule.entity.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import com.yuyan.imemodule.keyboard.keyIconRecords
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import java.util.Objects

/**
 * 按键的属性
 */
open class SoftKey {
    var keyCode = 0
    var stateId = 0
    private var mkeyLabel: String = ""
    private var mKeyLabelSmall: String = ""
    var keyMnemonic: String? = null

    /** 键盘上下左右位置百分比 ，mLeft = (int) (mLeftF * skbWidth);  */
    var mLeftF = -1f
    var mTopF = -1f
    var widthF = 0f
    var heightF = 0.25f

    /** 键盘上下左右位置坐标边界; */
    var mLeft = 0
    var mRight = 0
    var mTop = 0
    var mBottom = 0

    var pressed = false
    var keyType = KeyType.Normal

    constructor(code: Int = 0, label: String = "", labelSmall: String = "", keyMnemonic: String = "") {
        keyCode = code
        mkeyLabel = label
        mKeyLabelSmall = labelSmall
        this.keyMnemonic = keyMnemonic
    }

    fun onPressed() {
        pressed = true
    }

    fun onReleased() {
        pressed = false
    }

    fun setKeyDimensions(left: Float, top: Float) {
        mLeftF = left
        mTopF = top
    }
    fun setKeyDimensions(left: Float, top: Float, height:Float) {
        setKeyDimensions(left, top)
        heightF = height
    }

    /**
     * 设置按键的区域
     */
    fun setSkbCoreSize(skbWidth: Int, skbHeight: Int) {
        mLeft = (mLeftF * skbWidth).toInt()
        mRight = ((mLeftF + widthF) * skbWidth).toInt()
        mTop = (mTopF * skbHeight).toInt()
        mBottom = ((mTopF + heightF) * skbHeight).toInt()
    }

    open val keyIcon: Drawable?
        get() = keyIconRecords[Objects.hash(keyCode, stateId)]

    open val keyLabel: String
        get() =  mkeyLabel

    fun getmKeyLabelSmall(): String {
        return mKeyLabelSmall
    }

    fun getkeyLabel(): String {
        return mkeyLabel
    }

    open fun changeCase(upperCase: Boolean) {
        mkeyLabel = if (upperCase) mkeyLabel.uppercase() else mkeyLabel.lowercase()
    }

    val isKeyCodeKey: Boolean
        get() = keyCode > 0

    val isUserDefKey: Boolean
        get() = keyCode < 0

    val isUniStrKey: Boolean
        get() = keyCode == 0

    fun repeatable(): Boolean {
        return keyCode == KeyEvent.KEYCODE_DEL || keyCode == InputModeSwitcherManager.USER_DEF_KEYCODE_CURSOR_DIRECTION_9
                || keyCode in KeyEvent.KEYCODE_DPAD_UP .. KeyEvent.KEYCODE_DPAD_RIGHT
    }

    fun width(): Int {
        return mRight - mLeft
    }

    fun height(): Int {
        return mBottom - mTop
    }
}
