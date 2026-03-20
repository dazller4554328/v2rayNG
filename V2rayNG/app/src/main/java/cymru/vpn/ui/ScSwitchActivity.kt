package cymru.vpn.ui

import android.os.Bundle
import cymru.vpn.R
import cymru.vpn.handler.V2RayServiceManager

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (V2RayServiceManager.isRunning()) {
            V2RayServiceManager.stopVService(this)
        } else {
            V2RayServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }
}
