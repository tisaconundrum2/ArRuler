package com.android.ar_ruler_kt

/**
 * @author：TianLong
 * @date：2022/7/7 15:56
 * @detail：View operation interface
 */
interface IViewInterface {
    fun detectSuccess(msg:String)

    fun detectFailed(msg:String)
}