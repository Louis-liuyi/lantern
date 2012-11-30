package org.lantern;

import java.util.Timer;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.SystemUtils;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.lantern.http.GoogleOauth2CallbackServer;
import org.lantern.http.GoogleOauth2RedirectServlet;
import org.lantern.http.InteractionServlet;
import org.lantern.http.JettyLauncher;
import org.lantern.http.LanternApi;
import org.lantern.privacy.DefaultLocalCipherProvider;
import org.lantern.privacy.LocalCipherProvider;
import org.lantern.privacy.MacLocalCipherProvider;
import org.lantern.privacy.WindowsLocalCipherProvider;
import org.lantern.state.CometDSyncStrategy;
import org.lantern.state.DefaultModelChangeImplementor;
import org.lantern.state.Model;
import org.lantern.state.ModelChangeImplementor;
import org.lantern.state.ModelIo;
import org.lantern.state.SyncService;
import org.lantern.state.SyncStrategy;
import org.littleshoot.proxy.HttpRequestFilter;
import org.littleshoot.proxy.PublicIpsOnlyRequestFilter;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Names;

public class LanternModule extends AbstractModule { 
    
    @Override 
    protected void configure() {
        //bind(ChannelGroup.class).to(DefaultChannelGroup.class);
        bind(org.jboss.netty.util.Timer.class).to(HashedWheelTimer.class);
        bind(HttpRequestFilter.class).to(PublicIpsOnlyRequestFilter.class);
        
        bind(Stats.class).to(StatsTracker.class);
        
        bind(LanternSocketsUtil.class);
        bind(LanternXmppUtil.class);
        
        //bind(SystemTray.class).annotatedWith(WinOsxTray.class).to(SystemTrayImpl.class);
        //bind(SystemTray.class).annotatedWith(Names.named("facade")).to(FallbackTray.class);
        bind(MessageService.class).to(Dashboard.class);
        bind(Proxifier.class);
        bind(Configurator.class);
        
        bind(SyncStrategy.class).to(CometDSyncStrategy.class);
        bind(SyncService.class);
        bind(EncryptedFileService.class).to(DefaultEncryptedFileService.class);
        
        bind(BrowserService.class).to(ChromeBrowserService.class);
        
        bind(Model.class).toProvider(ModelIo.class);
        //bind(ModelProvider.class).to(ModelIo.class);
        
        bind(ModelChangeImplementor.class).to(DefaultModelChangeImplementor.class);
        bind(InteractionServlet.class);
        
        bind(LanternKeyStoreManager.class);
        bind(SslHttpProxyServer.class);
        bind(PlainTestRelayHttpProxyServer.class);
        
        bind(XmppHandler.class).to(DefaultXmppHandler.class);
        
        bind(PeerProxyManager.class).annotatedWith(Names.named("trusted")).to(TrustedPeerProxyManager.class);
        bind(PeerProxyManager.class).annotatedWith(Names.named("anon")).to(AnonymousPeerProxyManager.class);
        bind(GoogleOauth2CallbackServer.class);
        bind(GoogleOauth2RedirectServlet.class);
        bind(JettyLauncher.class);
        bind(AppIndicatorTray.class);
        
        bind(LanternApi.class).to(DefaultLanternApi.class);
        bind(SettingsChangeImplementor.class).to(DefaultSettingsChangeImplementor.class);
        bind(LanternHttpProxyServer.class);
    }
    
    @Provides
    SystemTray provideSystemTray(final XmppHandler handler, 
        final BrowserService browserService) {
        if (SystemUtils.IS_OS_LINUX) {
            return new AppIndicatorTray(browserService);
        } else {
            return new SystemTrayImpl(handler, browserService);
        }
    }
    
    @Provides
    ChannelGroup provideChannelGroup() {
        return new DefaultChannelGroup("LanternChannelGroup");
    }
    
    @Provides
    Timer provideTimer() {
        return new Timer("Lantern-Timer", true);
    }
    
    @Provides LocalCipherProvider provideLocalCipher(final MessageService messageService) {
        final LocalCipherProvider lcp; 
        
        /*
        if (!settings().isKeychainEnabled()) {
            lcp = new DefaultLocalCipherProvider();
        }
        */
        if (SystemUtils.IS_OS_WINDOWS) {
            lcp = new WindowsLocalCipherProvider();   
        }
        else if (SystemUtils.IS_OS_MAC_OSX) {
            lcp = new MacLocalCipherProvider(messageService);
        }
        // disabled per #249
        //else if (SystemUtils.IS_OS_LINUX && 
        //         SecretServiceLocalCipherProvider.secretServiceAvailable()) {
        //    lcp = new SecretServiceLocalCipherProvider();
        //}
        else {
            lcp = new DefaultLocalCipherProvider();
        }
        
        return lcp;
    }
    
    
    @Provides
    ServerSocketChannelFactory provideServerSocketChannelFactory() {
        return new NioServerSocketChannelFactory(
            Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat(
                    "Lantern-Netty-Server-Boss-Thread-%d").setDaemon(true).build()),
            Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat(
                    "Lantern-Netty-Server-Worker-Thread-%d").setDaemon(true).build()));
    }
    
    @Provides
    ClientSocketChannelFactory provideClientSocketChannelFactory() {
        return new NioClientSocketChannelFactory(
            Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat(
                    "Lantern-Netty-Client-Boss-Thread-%d").setDaemon(true).build()),
            Executors.newCachedThreadPool(
                new ThreadFactoryBuilder().setNameFormat(
                    "Lantern-Netty-Client-Worker-Thread-%d").setDaemon(true).build()));
    }
    
    public static void main(final String[] args) throws Exception {
        final Injector injector = Guice.createInjector(new LanternModule());
        //final ModelProvider model = injector.getInstance(ModelIo.class);
        
        final LanternService xmpp = injector.getInstance(DefaultXmppHandler.class);
        
        final SystemTray sys = injector.getInstance(SystemTrayImpl.class);
        final SystemTray sys2 = injector.getInstance(FallbackTray.class);
        
        System.out.println("CREATED SERVICE");
        xmpp.start();
        addShutdownHook(xmpp);
        //System.out.println("Got model: "+model);
        System.out.println("Got xmpp: "+xmpp);
        
        System.out.println("Got model: "+sys);
        System.out.println("Got xmpp: "+sys2);
    }

    private static void addShutdownHook(final LanternService service) {
        
        // TODO: Add these all to a single list of things to do on shutdown.
        final Thread serviceHook = new Thread(new Runnable() {
            @Override
            public void run() {
                service.stop();
            }
        }, "ShutdownHook-For-Service-"+service.getClass().getSimpleName());
        Runtime.getRuntime().addShutdownHook(serviceHook);
    }
}
