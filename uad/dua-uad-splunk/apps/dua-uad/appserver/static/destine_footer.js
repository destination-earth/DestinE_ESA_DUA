// setup paths for rest of the configuration
requireRoot = "../app";
appPath     = requireRoot + "/dua-uad";

// configure RequrieJS Paths and Options
require.config({
  paths: {
    "app"     : requireRoot,
    "feather" : appPath + "/feather",
  }
});

require(['jquery','feather'], function ($, feather) {

    $(document).ready(function () {
        // Load an HTML fragment into the #content div
        $('#destine_footer').load('../../static/app/dua-uad/destine_footer.htm', function (response, status, xhr) {
            if (status === "error") {
                console.error("Error loading file:", xhr.status, xhr.statusText);
                $('#destine_footer').html('<p>Error loading content.</p>');
            } else {
                console.log("Content loaded successfully.");
            }

            //---- footer_container.js
            let footer = document.getElementById('footer-container');
            let footerHeight = '64px';
            let footerMenu = document.getElementById('footer-menu');
            let footerMenuDiv = document.getElementById('footer-menu-div');
            let footerMenuIcon = document.getElementById('footer-menu-icon');

            let footerMenuIsVisible = false;
            let footerHidingTimeoutId;
            let footerMenuHidingTimeoutId;
            let footerHideTimeout = 4000;
            let footerMenuHideTimeout = 4000;

            let footerHasRevealed = true;
            let footerScrollPositionPrec = 0;
            let tempFooterPrevPos = 0;

            function hideFooter() {
                if (document.documentElement.scrollHeight != window.innerHeight && window.scrollY < (document.documentElement.scrollHeight - window.innerHeight - 50)) {
                    footerHasRevealed = false;
                    footer.style.transition = 'bottom 500ms ease-in-out, background-color 500ms ease-in-out';
                    footer.style.bottom = "-" + footerHeight;
                }
            }

            function showFooter() {
                footer.style.transition = 'bottom 250ms ease-in-out, background-color 500ms ease-in-out';
                footer.style.bottom = '0px';
                footer.style.position = 'fixed';
                setTimeout(() => {
                    footerHasRevealed = true;
                }, 500);
                clearTimeout(footerHidingTimeoutId);
            }

            /* Footer show/hide functions */
            window.addEventListener('scroll', (e) => {
                let deltaY = window.scrollY - tempFooterPrevPos;

                if (window.scrollY >= (document.documentElement.scrollHeight - window.innerHeight - 50)) {
                    showFooter();
                } else {
                    if (deltaY < 0 && deltaY > -100) {
                        setTimeout(() => {
                            footerScrollPositionPrec = window.scrollY;
                        }, 500);
                        if (footerHasRevealed) {
                            clearTimeout(footerHideTimeout);
                            hideFooter();
                            setTimeout(() => {
                                footerMenuIcon.setAttribute('data-feather', 'menu');
                                hideFooterMenu();
                                setFeather();
                            }, 500);
                        }
                    } else if (deltaY > 0 && deltaY < 100) {
                        if (window.scrollY - footerScrollPositionPrec > 0) {
                            showFooter();
                            footerHidingTimeoutId = setTimeout(() => {
                                hideFooter();
                            }, footerHideTimeout);
                        }
                    }
                }

                tempFooterPrevPos = window.scrollY;
            });

            footer.addEventListener('mouseover', () => {
                clearTimeout(footerHidingTimeoutId);
            });
            footer.addEventListener('mouseleave', () => {
                clearTimeout(footerHidingTimeoutId);
                footerHidingTimeoutId = setTimeout(() => {
                    hideFooter();
                }, footerHideTimeout);
            });
            document.onmousemove = (e) => {
                if (e.y > window.innerHeight - 50) {
                    showFooter();
                }
            }


            function hideFooterMenu() {
                footerMenu.style.display = 'none';
                footerMenuIsVisible = false;
            }

            function setupFooterMenuIcon() {
                footerMenuIcon.addEventListener('click', () => {
                    clearTimeout(footerMenuHidingTimeoutId);
                    if (footerMenuIsVisible) {
                        footerMenuIcon.setAttribute('data-feather', 'menu');
                        hideFooterMenu();
                    } else {
                        footerMenuIcon.setAttribute('data-feather', 'x');
                        footerMenu.style.display = 'flex';
                        footerMenuIsVisible = true;
                        footerMenuHidingTimeoutId = setTimeout(() => {
                            footerMenuIcon.setAttribute('data-feather', 'menu');
                            hideFooterMenu();
                            setFeather();
                        }, footerMenuHideTimeout);
                    }
                    setFeather();
                });
                footerMenu.addEventListener('mouseover', (e) => {
                    clearTimeout(footerMenuHidingTimeoutId);
                    clearTimeout(footerHidingTimeoutId);
                });
                footerMenu.addEventListener('mouseleave', (e) => {
                    clearTimeout(footerMenuHidingTimeoutId);
                    footerMenuHidingTimeoutId = setTimeout(() => {
                        footerMenuIcon.setAttribute('data-feather', 'menu');
                        hideFooterMenu();
                        setFeather();
                    }, footerMenuHideTimeout);
                    clearTimeout(footerHidingTimeoutId);
                    footerHidingTimeoutId = setTimeout(() => {
                        footer.style.transition = 'bottom 500ms ease-in-out, background-color 500ms ease-in-out';
                        footer.style.bottom = "-" + footerHeight;
                    }, footerHideTimeout);
                });
            }

            function setFeather() {
                feather.replace();
                footerMenuIcon = document.getElementById('footer-menu-icon');
                setupFooterMenuIcon();
            }

            setFeather();
            //----

        });

    });

});