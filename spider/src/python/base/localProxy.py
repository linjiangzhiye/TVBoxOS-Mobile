from com.github.catvod import Proxy as CatVodProxy

class Proxy:
    def getUrl(self, local):
        return CatVodProxy.getUrl().rstrip('/')

    def getPort(self):
        return CatVodProxy.get()
